package com.jetbrains.jsonSchema.impl;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.PairConvertor;
import com.intellij.util.ThrowablePairConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.*;

/**
 * @author Irina.Chernushina on 8/27/2015.
 */
public class JsonSchemaReader {
  public static final Logger LOG = Logger.getInstance("#com.jetbrains.jsonSchema.impl.JsonSchemaReader");
  public static final NotificationGroup ERRORS_NOTIFICATION = NotificationGroup.logOnlyGroup("JSON Schema");
  @Nullable private final VirtualFile myKey;

  public JsonSchemaReader(@Nullable final VirtualFile key) {
    myKey = key;
  }

  public JsonSchemaObject read(@NotNull final Reader reader, @Nullable JsonSchemaExportedDefinitions definitions) throws IOException {
    final JsonReader in = new JsonReader(reader);
    in.setLenient(true);

    in.beginObject();
    final JsonSchemaObject object = new JsonSchemaObject();
    final JsonSchemaGeneralObjectTypeAdapter adapter = new JsonSchemaGeneralObjectTypeAdapter();

    while (in.peek() == JsonToken.NAME) {
      final String name = in.nextName();
      adapter.readSomeProperty(in, name, object);
    }

    processReferences(object, adapter.getAllObjects(), definitions);
    final ArrayList<JsonSchemaObject> withoutDefinitions = new ArrayList<>(adapter.getAllObjects());
    removeDefinitions(object, withoutDefinitions);
    return object;
  }

  public static boolean isJsonSchema(@NotNull JsonSchemaExportedDefinitions definitions,
                                     @NotNull VirtualFile key,
                                     @NotNull final String string,
                                     Consumer<String> errorConsumer) throws IOException {
    final JsonSchemaReader reader = new JsonSchemaReader(key);
    java.io.StringReader stringReader = new java.io.StringReader(string);
    try {
      reader.read(stringReader, null);
    } catch (Exception e) {
      LOG.info(e);
      errorConsumer.consume(e.getMessage());
      return false;
    }
    // have two stages so that just syntax errors do not clear cache
    stringReader = new java.io.StringReader(string);
    try {
      reader.read(stringReader, definitions);
    }
    catch (Exception e) {
      LOG.info(e);
      errorConsumer.consume(e.getMessage());
      throw e;
    }
    return true;
  }

  public static void registerObjectsExportedDefinitions(@NotNull VirtualFile key,
                                                        @NotNull final JsonSchemaExportedDefinitions definitionsObject,
                                                        @NotNull final JsonSchemaObject object) {
    String id = object.getId();
    if (!StringUtil.isEmptyOrSpaces(id)) {
      id = id.endsWith("#") ? id.substring(0, id.length() - 1) : id;
      final PairConvertor<String, Map<String, JsonSchemaObject>, Map<String, JsonSchemaObject>> convertor =
        (s, map) -> {
          final Map<String, JsonSchemaObject> converted = new HashMap<>();
          for (Map.Entry<String, JsonSchemaObject> entry : map.entrySet()) {
            String key1 = entry.getKey();
            key1 = key1.startsWith("/") ? key1.substring(1) : key1;
            converted.put(s + key1, entry.getValue());
          }
          return converted;
        };

      final HashMap<String, JsonSchemaObject> map = new HashMap<>();
      map.put("", object);
      final Map<String, JsonSchemaObject> definitions = object.getDefinitions();
      if (definitions != null && !definitions.isEmpty()) {
        map.putAll(convertor.convert("#/definitions/", definitions));
      }
      final Map<String, JsonSchemaObject> properties = object.getProperties();
      if (properties != null && !properties.isEmpty()) {
        map.putAll(convertor.convert("#/properties/", properties));
      }
      definitionsObject.register(key, id, map);
    }
  }

  private static void removeDefinitions(JsonSchemaObject root, ArrayList<JsonSchemaObject> objects) {
    final List<JsonSchemaObject> queue = new ArrayList<>(objects.size() + 1);
    queue.addAll(objects);
    queue.add(root);

    for (JsonSchemaObject object : queue) {
      final Map<String, JsonSchemaObject> definitions = object.getDefinitions();
      if (definitions != null) {
        objects.removeAll(definitions.values());
      }
    }
  }

  private void processReferences(JsonSchemaObject root,
                                 Set<JsonSchemaObject> objects,
                                 @Nullable JsonSchemaExportedDefinitions definitions) {
    final ArrayDeque<JsonSchemaObject> queue = new ArrayDeque<>();
    queue.addAll(objects);
    int control = 10000;

    while (!queue.isEmpty()) {
      if (--control == 0) throw new RuntimeException("cyclic definitions search");

      final JsonSchemaObject current = queue.removeFirst();
      if ("#".equals(current.getRef())) continue;
      if (current.getRef() != null) {
        final JsonSchemaObject definition = findDefinition(myKey, current.getRef(), root, definitions);
        if (definition == null) {
          if (definitions == null) {
            // just skip current item
            current.setRef(null);
            continue;
          }
          throw new RuntimeException("Can not find definition: " + current.getRef());
        }
        if (definition.getRef() != null && !"#".equals(definition.getRef())) {
          queue.addFirst(current);
          queue.addFirst(definition);
          continue;
        }

        final JsonSchemaObject copy = new JsonSchemaObject();
        copy.setDefinitionAddress(current.getRef());
        copy.mergeValues(definition);
        copy.mergeValues(current);
        current.copyValues(copy);
        current.setRef(null);
      }
    }
  }

  private static JsonSchemaObject findAbsoluteDefinition(@Nullable VirtualFile key,
                                                         @NotNull String ref,
                                                         @Nullable JsonSchemaExportedDefinitions definitions) {
    if (!ref.startsWith("#/")) {
      if (definitions == null || key == null) return null;
      int idx = ref.indexOf("#/");
      final String url;
      final String relative;
      if (idx == -1) {
        url = ref.endsWith("#") ? ref.substring(0, ref.length() - 1) : ref;
        relative = "";
      } else {
        url = ref.substring(0, idx);
        relative = ref.substring(idx);
      }
      return definitions.findDefinition(key, url, relative);
    }
    return null;
  }

  @Nullable
  private static JsonSchemaObject findDefinition(@Nullable VirtualFile key,
                                                @NotNull String ref,
                                                @NotNull final JsonSchemaObject root,
                                                @Nullable JsonSchemaExportedDefinitions definitions) {
    if ("#".equals(ref)) {
      return root;
    }
    if (!ref.startsWith("#/")) {
      return findAbsoluteDefinition(key, ref, definitions);
    }
    return findRelativeDefinition(ref, root);
  }

  @NotNull
  public static JsonSchemaObject findRelativeDefinition(@NotNull String ref, @NotNull JsonSchemaObject root) {
    if ("#".equals(ref)) {
      return root;
    }
    if (!ref.startsWith("#/")) throw new RuntimeException("Non-relative or erroneous reference: " + ref);
    ref = ref.substring(2);
    final String[] parts = ref.split("/");
    JsonSchemaObject current = root;
    for (int i = 0; i < parts.length; i++) {
      if (current == null) throw new RuntimeException("Incorrect reference: " + ref);
      final String part = parts[i];
      if ("definitions".equals(part)) {
        if (i == (parts.length - 1)) throw new RuntimeException("Incorrect definition reference: " + ref);
        //noinspection AssignmentToForLoopParameter
        current = current.getDefinitions().get(parts[++i]);
        continue;
      }
      if ("properties".equals(part)) {
        if (i == (parts.length - 1)) throw new RuntimeException("Incorrect properties reference: " + ref);
        //noinspection AssignmentToForLoopParameter
        current = current.getProperties().get(parts[++i]);
        continue;
      }

      current = current.getDefinitions().get(part);
    }
    if (current == null) throw new RuntimeException("Incorrect reference: " + ref);
    return current;
  }

  private static class JsonSchemaGeneralObjectTypeAdapter extends TypeAdapter<JsonSchemaObject> {
    private final Set<JsonSchemaObject> myAllObjects = new HashSet<>();
    private final Map<String, JsonSchemaObject> myIds = new HashMap<>();

    private final Map<String, ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException>> myMap;

    public JsonSchemaGeneralObjectTypeAdapter() {
      myMap = new HashMap<>();
      myMap.put("id", new StringReader() {
        @Override
        protected void assign(String s, JsonSchemaObject object) throws IOException {
          object.setId(s);
        }
      });
      myMap.put("$schema", new StringReader() {
        @Override
        protected void assign(String s, JsonSchemaObject object) throws IOException {
          object.setSchema(s);
        }
      });
      myMap.put("description", new StringReader() {
        @Override
        protected void assign(String s, JsonSchemaObject object) throws IOException {
          object.setDescription(s);
        }
      });
      myMap.put("title", new StringReader() {
        @Override
        protected void assign(String s, JsonSchemaObject object) throws IOException {
          object.setTitle(s);
        }
      });
      myMap.put("$ref", createRef());
      myMap.put("default", createDefault());
      myMap.put("format", createFormat());
      myMap.put("definitions", createDefinitionsConsumer());
      myMap.put("properties", createPropertiesConsumer());
      myMap.put("multipleOf", createMultipleOf());
      myMap.put("maximum", createMaximum());
      myMap.put("minimum", createMinimum());
      myMap.put("exclusiveMaximum", createExclusiveMaximum());
      myMap.put("exclusiveMinimum", createExclusiveMinimum());
      myMap.put("maxLength", createMaxLength());
      myMap.put("minLength", createMinLength());
      myMap.put("pattern", createPattern());
      myMap.put("additionalItems", createAdditionalItems());
      myMap.put("items", createItems());
      myMap.put("maxItems", createMaxItems());
      myMap.put("minItems", createMinItems());
      myMap.put("uniqueItems", createUniqueItems());
      myMap.put("maxProperties", createMaxProperties());
      myMap.put("minProperties", createMinProperties());
      myMap.put("required", createRequired());
      myMap.put("additionalProperties", createAdditionalProperties());
      myMap.put("patternProperties", createPatternProperties());
      myMap.put("dependencies", createDependencies());
      myMap.put("enum", createEnum());
      myMap.put("type", createType());
      myMap.put("allOf", new SchemaArrayConsumer() {
        @Override
        protected void assign(ArrayList<JsonSchemaObject> list, JsonSchemaObject object) {
          object.setAllOf(list);
        }
      });
      myMap.put("anyOf", new SchemaArrayConsumer() {
        @Override
        protected void assign(ArrayList<JsonSchemaObject> list, JsonSchemaObject object) {
          object.setAnyOf(list);
        }
      });
      myMap.put("oneOf", new SchemaArrayConsumer() {
        @Override
        protected void assign(ArrayList<JsonSchemaObject> list, JsonSchemaObject object) {
          object.setOneOf(list);
        }
      });
      myMap.put("not", createNot());
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createFormat() {
      return new StringReader() {
        @Override
        protected void assign(String s, JsonSchemaObject object) throws IOException {
          object.setFormat(s);
        }
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createDefault() {
      return (in, object) -> {
        if (in.peek() == JsonToken.BEGIN_OBJECT) {
          object.setDefault(readInnerObject(in));
        } else if (in.peek() == JsonToken.NUMBER) {
          object.setDefault(in.nextDouble());
        } else if (in.peek() == JsonToken.STRING) {
          object.setDefault(in.nextString());
        } else if (in.peek() == JsonToken.BOOLEAN) {
          object.setDefault(in.nextBoolean());
        } else in.skipValue();
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createRef() {
      return new StringReader() {
        @Override
        protected void assign(String s, JsonSchemaObject object) throws IOException {
          object.setRef(s);
        }
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createNot() {
      return (in, object) -> {
        if (in.peek() == JsonToken.BEGIN_OBJECT) {
          object.setNot(readInnerObject(in));
        } else in.skipValue();
      };
    }

    private abstract class SchemaArrayConsumer implements ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> {
      @Override
      public void consume(JsonReader in, JsonSchemaObject object) throws IOException {
        if (in.peek() == JsonToken.BEGIN_ARRAY) {
          in.beginArray();
          final ArrayList<JsonSchemaObject> list = new ArrayList<>();
          while (in.peek() != JsonToken.END_ARRAY) {
            if (in.peek() == JsonToken.BEGIN_OBJECT) {
              list.add(readInnerObject(in));
            } else in.skipValue();
          }
          assign(list, object);
          in.endArray();
        } else in.skipValue();
      }

      protected abstract void assign(ArrayList<JsonSchemaObject> list, JsonSchemaObject object);
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createType() {
      return new ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException>() {
        @Override
        public void consume(JsonReader in, JsonSchemaObject object) throws IOException {
          if (in.peek() == JsonToken.STRING) {
            object.setType(parseType(in));
          } else if (in.peek() == JsonToken.BEGIN_ARRAY) {
            final ArrayList<JsonSchemaType> variants = new ArrayList<>();
            in.beginArray();
            while (in.peek() != JsonToken.END_ARRAY) {
              if (in.peek() == JsonToken.STRING) {
                variants.add(parseType(in));
              } else in.skipValue();
            }
            in.endArray();
            object.setTypeVariants(variants);
          } else in.skipValue();
        }

        private JsonSchemaType parseType(JsonReader in) throws IOException {
          final String typeString = in.nextString();
          try {
            return JsonSchemaType.valueOf("_" + typeString);
          } catch (IllegalArgumentException e) {
            throw new IOException("Wrong type value: " + typeString + "\"");
          }
        }
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createEnum() {
      return (in, object) -> {
        if (in.peek() != JsonToken.BEGIN_ARRAY) {
          in.skipValue();
          return;
        }
        final ArrayList<Object> objects = new ArrayList<>();
        in.beginArray();
        while (in.peek() != JsonToken.END_ARRAY) {
          if (in.peek() == JsonToken.STRING) objects.add("\"" + in.nextString() + "\"");
          else if (in.peek() == JsonToken.NUMBER) objects.add(in.nextInt());  // parse as integer here makes much more sense
          else if (in.peek() == JsonToken.BOOLEAN) objects.add(in.nextBoolean());
          else in.skipValue();
        }
        in.endArray();
        object.setEnum(objects);
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createDependencies() {
      return (in, object) -> {
        if (in.peek() != JsonToken.BEGIN_OBJECT) {
          in.skipValue();
          return;
        }
        final HashMap<String, List<String>> propertyDependencies = new HashMap<>();
        final HashMap<String, JsonSchemaObject> schemaDependencies = new HashMap<>();
        in.beginObject();
        while (in.peek() != JsonToken.END_OBJECT) {
          if (in.peek() != JsonToken.NAME) {
            in.skipValue();
            continue;
          }
          final String name = in.nextName();
          if (in.peek() == JsonToken.BEGIN_ARRAY) {
            final List<String> members = new ArrayList<>();
            in.beginArray();
            while (in.peek() != JsonToken.END_ARRAY) {
              if (in.peek() == JsonToken.STRING) {
                members.add(in.nextString());
              } else in.skipValue();
            }
            in.endArray();
            propertyDependencies.put(name, members);
          } else if (in.peek() == JsonToken.BEGIN_OBJECT) {
            schemaDependencies.put(name, readInnerObject(in));
          } else in.skipValue();
        }
        in.endObject();
        if (! propertyDependencies.isEmpty()) {
          object.setPropertyDependencies(propertyDependencies);
        }
        if (! schemaDependencies.isEmpty()) {
          object.setSchemaDependencies(schemaDependencies);
        }
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createPatternProperties() {
      return (in, object) -> {
        if (in.peek() != JsonToken.BEGIN_OBJECT) {
          in.skipValue();
          return;
        }
        in.beginObject();
        final HashMap<String, JsonSchemaObject> properties = new HashMap<>();
        while (in.peek() != JsonToken.END_OBJECT) {
          if (in.peek() == JsonToken.NAME) {
            final String name = in.nextName();
            if (in.peek() == JsonToken.BEGIN_OBJECT) {
              properties.put(name, readInnerObject(in));
            } else in.skipValue();
          } else in.skipValue();
        }
        object.setPatternProperties(properties);
        in.endObject();
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createAdditionalProperties() {
      return (in, object) -> {
        if (in.peek() == JsonToken.BOOLEAN) {
          object.setAdditionalPropertiesAllowed(in.nextBoolean());
        } else if (in.peek() == JsonToken.BEGIN_OBJECT) {
          object.setAdditionalPropertiesSchema(readInnerObject(in));
        } else {
          in.skipValue();
        }
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createRequired() {
      return (in, object) -> {
        if (in.peek() == JsonToken.BEGIN_ARRAY) {
          final ArrayList<String> required = new ArrayList<>();
          in.beginArray();
          while (in.peek() != JsonToken.END_ARRAY) {
            if (in.peek() == JsonToken.STRING) {
              required.add(in.nextString());
            } else {
              in.skipValue();
            }
          }
          in.endArray();
          object.setRequired(required);
        } else {
          in.skipValue();
        }
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createMinProperties() {
      return new NumberReader() {
        @Override
        protected void readNumber(JsonReader in, JsonSchemaObject object) throws IOException {
          object.setMinProperties(in.nextInt());
        }
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createMaxProperties() {
      return new NumberReader() {
        @Override
        protected void readNumber(JsonReader in, JsonSchemaObject object) throws IOException {
          object.setMaxProperties(in.nextInt());
        }
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createUniqueItems() {
      return new BooleanReader() {
        @Override
        protected void assign(boolean b, JsonSchemaObject object) throws IOException {
          object.setUniqueItems(b);
        }
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createMinItems() {
      return new NumberReader() {
        @Override
        protected void readNumber(JsonReader in, JsonSchemaObject object) throws IOException {
          object.setMinItems(in.nextInt());
        }
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createMaxItems() {
      return new NumberReader() {
        @Override
        protected void readNumber(JsonReader in, JsonSchemaObject object) throws IOException {
          object.setMaxItems(in.nextInt());
        }
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createItems() {
      return (in, object) -> {
        if (in.peek() == JsonToken.BEGIN_OBJECT) {
          object.setItemsSchema(readInnerObject(in));
        } else if (in.peek() == JsonToken.BEGIN_ARRAY) {
          in.beginArray();
          final List<JsonSchemaObject> list = new ArrayList<>();
          while (in.peek() != JsonToken.END_ARRAY) {
            if (in.peek() == JsonToken.BEGIN_OBJECT) {
              list.add(readInnerObject(in));
            } else in.skipValue();
          }
          in.endArray();
          object.setItemsSchemaList(list);
        }
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createAdditionalItems() {
      return (in, object) -> {
        if (in.peek() == JsonToken.BOOLEAN) {
          object.setAdditionalItemsAllowed(in.nextBoolean());
        } else if (in.peek() == JsonToken.BEGIN_OBJECT) {
          object.setAdditionalItemsSchema(readInnerObject(in));
        } else {
          in.skipValue();
        }
      };
    }

    private JsonSchemaObject readInnerObject(JsonReader in) throws IOException {
      return read(in);
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createPattern() {
      return new StringReader() {
        @Override
        protected void assign(String s, JsonSchemaObject object) throws IOException {
          object.setPattern(s);
        }
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createMinLength() {
      return new NumberReader() {
        @Override
        protected void readNumber(JsonReader in, JsonSchemaObject object) throws IOException {
          object.setMinLength(in.nextInt());
        }
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createMaxLength() {
      return new NumberReader() {
        @Override
        protected void readNumber(JsonReader in, JsonSchemaObject object) throws IOException {
          object.setMaxLength(in.nextInt());
        }
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createExclusiveMinimum() {
      return new BooleanReader() {
        @Override
        protected void assign(boolean b, JsonSchemaObject object) throws IOException {
          object.setExclusiveMinimum(b);
        }
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createExclusiveMaximum() {
      return new BooleanReader() {
        @Override
        protected void assign(boolean b, JsonSchemaObject object) throws IOException {
          object.setExclusiveMaximum(b);
        }
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createMinimum() {
      return new NumberReader() {
        @Override
        protected void readNumber(JsonReader in, JsonSchemaObject object) throws IOException {
          object.setMinimum(in.nextDouble());
        }
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createMaximum() {
      return new NumberReader() {
        @Override
        protected void readNumber(JsonReader in, JsonSchemaObject object) throws IOException {
          object.setMaximum(in.nextDouble());
        }
      };
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createMultipleOf() {
      return new NumberReader() {
        @Override
        protected void readNumber(JsonReader in, JsonSchemaObject object) throws IOException {
          object.setMultipleOf(in.nextDouble());
        }
      };
    }

    private abstract class StringReader implements ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> {
      @Override
      public void consume(JsonReader in, JsonSchemaObject object) throws IOException {
        if (in.peek() == JsonToken.STRING) {
          assign(in.nextString(), object);
        } else {
          in.skipValue();
        }
      }

      protected abstract void assign(String s, JsonSchemaObject object) throws IOException;
    }

    private abstract class BooleanReader implements ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> {
      @Override
      public void consume(JsonReader in, JsonSchemaObject object) throws IOException {
        if (in.peek() == JsonToken.BOOLEAN) {
          assign(in.nextBoolean(), object);
        } else {
          in.skipValue();
        }
      }

      protected abstract void assign(boolean b, JsonSchemaObject object) throws IOException;
    }

    private abstract class NumberReader implements ThrowablePairConsumer<JsonReader,JsonSchemaObject,IOException> {
      @Override
      public void consume(JsonReader in, JsonSchemaObject object) throws IOException {
        if (in.peek() == JsonToken.NUMBER) {
          readNumber(in, object);
        } else {
          in.skipValue();
        }
      }

      protected abstract void readNumber(JsonReader in, JsonSchemaObject object) throws IOException;
    }

    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createPropertiesConsumer() {
      return (in, object) -> {
        in.beginObject();
        while (in.peek() == JsonToken.NAME) {
          final String name = in.nextName();
          object.getProperties().put(name, readInnerObject(in));
        }
        in.endObject();
      };
    }

    @NotNull
    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> createDefinitionsConsumer() {
      return (in, object) -> {
        final Map<String, JsonSchemaObject> map = new HashMap<>();
        in.beginObject();
        while (in.peek() == JsonToken.NAME) {
          final String name = in.nextName();
          map.put(name, readInnerObject(in));
        }
        in.endObject();
        if (! map.isEmpty()) {
          object.setDefinitions(map);
        }
      };
    }

    @Nullable
    private ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> getPropertyConsumer(final String name) {
      return myMap.get(name);
    }

    @Override
    public void write(JsonWriter out, JsonSchemaObject value) throws IOException {
      throw new IllegalStateException(" no intention to implement writing");
    }

    @Override
    public JsonSchemaObject read(JsonReader in) throws IOException {
      in.beginObject();
      final JsonSchemaObject object = new JsonSchemaObject();
      while (in.peek() == JsonToken.NAME) {
        final String name = in.nextName();
        readSomeProperty(in, name, object);
      }
      in.endObject();
      myAllObjects.add(object);
      if (object.getId() != null) {
        myIds.put(object.getId(), object);
      }
      return object;
    }

    void readSomeProperty(JsonReader in, String name, JsonSchemaObject object) throws IOException {
      final ThrowablePairConsumer<JsonReader, JsonSchemaObject, IOException> consumer = myMap.get(name);
      if (consumer != null) {
        consumer.consume(in, object);
      }
      else {
        readSingleDefinition(in, name, object);
      }
    }

    void readSingleDefinition(JsonReader in, String name, JsonSchemaObject object) throws IOException {
      if (in.peek() != JsonToken.BEGIN_OBJECT) {
        in.skipValue();  // if unknown property has non-object value, than it is not a definition, lets ignore it
        return;
      }
      final JsonSchemaObject defined = read(in);
      if (defined == null) return;
      Map<String, JsonSchemaObject> definitions = object.getDefinitions();
      if (definitions == null) {
        object.setDefinitions(definitions = new HashMap<>());
      }
      definitions.put(name, defined);
    }

    public Set<JsonSchemaObject> getAllObjects() {
      return myAllObjects;
    }

    public Map<String, JsonSchemaObject> getIds() {
      return myIds;
    }
  }
}
