/*
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.gson;

import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dagger.Module;
import dagger.ObjectGraph;
import dagger.Provides;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.Decoder;
import feign.codec.Encoder;
import org.testng.annotations.Test;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;

@Test
public class GsonModuleTest {
  @Module(includes = GsonModule.class, injects = EncoderAndDecoderBindings.class)
  static class EncoderAndDecoderBindings {
    @Inject Encoder encoder;
    @Inject Decoder decoder;
  }

  @Test public void providesEncoderDecoder() throws Exception {
    EncoderAndDecoderBindings bindings = new EncoderAndDecoderBindings();
    ObjectGraph.create(bindings).inject(bindings);

    assertEquals(bindings.encoder.getClass(), GsonCodec.class);
    assertEquals(bindings.decoder.getClass(), GsonCodec.class);
  }

  @Module(includes = GsonModule.class, injects = EncoderBindings.class)
  static class EncoderBindings {
    @Inject Encoder encoder;
  }

  @Test public void encodesMapObjectNumericalValuesAsInteger() throws Exception {
    EncoderBindings bindings = new EncoderBindings();
    ObjectGraph.create(bindings).inject(bindings);

    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("foo", 1);

    RequestTemplate template = new RequestTemplate();
    bindings.encoder.encode(map, template);
    assertEquals(template.body(), ""//
        + "{\n" //
        + "  \"foo\": 1\n" //
        + "}");
  }

  @Test public void encodesFormParams() throws Exception {

    EncoderBindings bindings = new EncoderBindings();
    ObjectGraph.create(bindings).inject(bindings);

    Map<String, Object> form = new LinkedHashMap<String, Object>();
    form.put("foo", 1);
    form.put("bar", Arrays.asList(2, 3));

    RequestTemplate template = new RequestTemplate();
    bindings.encoder.encode(form, template);
    assertEquals(template.body(), ""//
        + "{\n" //
        + "  \"foo\": 1,\n" //
        + "  \"bar\": [\n" //
        + "    2,\n" //
        + "    3\n" //
        + "  ]\n" //
        + "}");
  }

  static class Zone extends LinkedHashMap<String, Object> {
    Zone() {
      // for reflective instantiation.
    }

    Zone(String name) {
      this(name, null);
    }

    Zone(String name, String id) {
      put("name", name);
      if (id != null)
        put("id", id);
    }

    private static final long serialVersionUID = 1L;
  }

  @Module(includes = GsonModule.class, injects = DecoderBindings.class)
  static class DecoderBindings {
    @Inject Decoder decoder;
  }

  @Test public void decodes() throws Exception {
    DecoderBindings bindings = new DecoderBindings();
    ObjectGraph.create(bindings).inject(bindings);

    List<Zone> zones = new LinkedList<Zone>();
    zones.add(new Zone("denominator.io."));
    zones.add(new Zone("denominator.io.", "ABCD"));

    Response response = Response.create(200, "OK", Collections.<String, Collection<String>>emptyMap(), zonesJson);
    assertEquals(bindings.decoder.decode(response, new TypeToken<List<Zone>>() {
    }.getType()), zones);
  }

  @Test public void nullBodyDecodesToNull() throws Exception {
    DecoderBindings bindings = new DecoderBindings();
    ObjectGraph.create(bindings).inject(bindings);

    Response response = Response.create(204, "OK", Collections.<String, Collection<String>>emptyMap(), null);
    assertEquals(bindings.decoder.decode(response, String.class), null);
  }

  private String zonesJson = ""//
      + "[\n"//
      + "  {\n"//
      + "    \"name\": \"denominator.io.\"\n"//
      + "  },\n"//
      + "  {\n"//
      + "    \"name\": \"denominator.io.\",\n"//
      + "    \"id\": \"ABCD\"\n"//
      + "  }\n"//
      + "]\n";

  @Module(includes = GsonModule.class, injects = CustomTypeAdapter.class)
  static class CustomTypeAdapter {
    @Provides(type = Provides.Type.SET) TypeAdapter upperZone() {
      return new TypeAdapter<Zone>() {

        @Override public void write(JsonWriter out, Zone value) throws IOException {
          throw new IllegalArgumentException();
        }

        @Override public Zone read(JsonReader in) throws IOException {
          in.beginObject();
          Zone zone = new Zone();
          while (in.hasNext()) {
            zone.put(in.nextName(), in.nextString().toUpperCase());
          }
          in.endObject();
          return zone;
        }
      };
    }

    @Inject Decoder decoder;
  }

  @Test public void customDecoder() throws Exception {
    CustomTypeAdapter bindings = new CustomTypeAdapter();
    ObjectGraph.create(bindings).inject(bindings);

    List<Zone> zones = new LinkedList<Zone>();
    zones.add(new Zone("DENOMINATOR.IO."));
    zones.add(new Zone("DENOMINATOR.IO.", "ABCD"));

    Response response = Response.create(200, "OK", Collections.<String, Collection<String>>emptyMap(), zonesJson);
    assertEquals(bindings.decoder.decode(response, new TypeToken<List<Zone>>() {
    }.getType()), zones);
  }
}
