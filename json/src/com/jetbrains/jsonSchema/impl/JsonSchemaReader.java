// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.*;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Irina.Chernushina on 1/13/2017.
 */
public class JsonSchemaReader {
  private static final int MAX_SCHEMA_LENGTH = FileUtilRt.MEGABYTE;
  public static final Logger LOG = Logger.getInstance(JsonSchemaReader.class);
  public static final NotificationGroup ERRORS_NOTIFICATION = NotificationGroup.logOnlyGroup("JSON Schema");

  private final Map<String, JsonSchemaObject> myIds = new HashMap<>();
  private final ArrayDeque<JsonSchemaObject> myQueue;

  private static final Map<String, MyReader> READERS_MAP = new HashMap<>();
  static {
    fillMap();
  }

  public JsonSchemaReader() {
    myQueue = new ArrayDeque<>();
  }

  public static JsonSchemaObject readFromFile(@NotNull Project project, @NotNull VirtualFile key) throws Exception {
    if (!key.isValid()) throw new Exception(String.format("Can not load JSON Schema file '%s'", key.getName()));
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(key);
    if (!(psiFile instanceof JsonFile)) throw new Exception(String.format("Can not load PSI for JSON Schema file '%s'", key.getName()));
    final JsonObject value = ObjectUtils.tryCast(((JsonFile)psiFile).getTopLevelValue(), JsonObject.class);
    if (value == null)
      throw new Exception(String.format("JSON Schema file '%s' must contain only one top-level object", key.getName()));
    return new JsonSchemaReader().read(value);
  }

  @Nullable
  public static String checkIfValidJsonSchema(@NotNull final Project project, @NotNull final VirtualFile file) {
    final long length = file.getLength();
    final String fileName = file.getName();
    if (length > MAX_SCHEMA_LENGTH) {
      return String.format("JSON schema was not loaded from '%s' because it's too large (file size is %d bytes).", fileName, length);
    }
    if (length == 0) {
      return String.format("JSON schema was not loaded from '%s'. File is empty.", fileName);
    }
    try {
      readFromFile(project, file);
    } catch (Exception e) {
      final String message = String.format("JSON Schema not found or contain error in '%s': %s", fileName, e.getMessage());
      LOG.info(message);
      return message;
    }
    return null;
  }

  public JsonSchemaObject read(@NotNull final JsonObject object) {
    final JsonSchemaObject root = new JsonSchemaObject(object);
    myQueue.add(root);
    while (!myQueue.isEmpty()) {
      final JsonSchemaObject currentSchema = myQueue.removeFirst();

      final JsonContainer jsonObject = currentSchema.getJsonObject();
      if (jsonObject instanceof JsonObject) {
        final List<JsonProperty> list = ((JsonObject)jsonObject).getPropertyList();
        for (JsonProperty property : list) {
          if (StringUtil.isEmptyOrSpaces(property.getName()) || property.getValue() == null) continue;
          final MyReader reader = READERS_MAP.get(property.getName());
          if (reader != null) {
            reader.read(property.getValue(), currentSchema, myQueue);
          }
          else {
            readSingleDefinition(property.getName(), property.getValue(), currentSchema);
          }
        }
      }
      else if (jsonObject instanceof JsonArray) {
        List<JsonValue> values = ((JsonArray)jsonObject).getValueList();
        for (int i = 0; i < values.size(); i++) {
          readSingleDefinition(String.valueOf(i), values.get(i), currentSchema);
        }
      }

      if (currentSchema.getId() != null) myIds.put(currentSchema.getId(), currentSchema);
    }
    return root;
  }

  public Map<String, JsonSchemaObject> getIds() {
    return myIds;
  }

  private void readSingleDefinition(@NotNull String name, @NotNull JsonValue value, @NotNull JsonSchemaObject schema) {
    if (value instanceof JsonContainer) {
      final JsonSchemaObject defined = new JsonSchemaObject((JsonContainer)value);
      myQueue.add(defined);
      Map<String, JsonSchemaObject> definitions = schema.getDefinitionsMap();
      if (definitions == null) schema.setDefinitionsMap(definitions = new HashMap<>());
      definitions.put(name, defined);
    }
  }

  private static void fillMap() {
    READERS_MAP.put("$id", (element, object, queue) -> {
      if (element instanceof JsonStringLiteral) object.setId(StringUtil.unquoteString(element.getText()));
    });
    READERS_MAP.put("id", (element, object, queue) -> {
      if (element instanceof JsonStringLiteral) object.setId(StringUtil.unquoteString(element.getText()));
    });
    READERS_MAP.put("$schema", (element, object, queue) -> {
      if (element instanceof JsonStringLiteral) object.setSchema(StringUtil.unquoteString(element.getText()));
    });
    READERS_MAP.put("description", (element, object, queue) -> {
      if (element instanceof JsonStringLiteral) object.setDescription(StringUtil.unquoteString(element.getText()));
    });
    READERS_MAP.put(JsonSchemaObject.X_INTELLIJ_HTML_DESCRIPTION, (element, object, queue) -> {
      if (element instanceof JsonStringLiteral) object.setHtmlDescription(StringUtil.unquoteString(element.getText()));
    });
    READERS_MAP.put("title", (element, object, queue) -> {
      if (element instanceof JsonStringLiteral) object.setTitle(StringUtil.unquoteString(element.getText()));
    });
    READERS_MAP.put("$ref", (element, object, queue) -> {
      if (element instanceof JsonStringLiteral) object.setRef(StringUtil.unquoteString(element.getText()));
    });
    READERS_MAP.put("default", createDefault());
    READERS_MAP.put("format", (element, object, queue) -> {
      if (element instanceof JsonStringLiteral) object.setFormat(StringUtil.unquoteString(element.getText()));
    });
    READERS_MAP.put(JsonSchemaObject.DEFINITIONS, createDefinitionsConsumer());
    READERS_MAP.put(JsonSchemaObject.PROPERTIES, createPropertiesConsumer());
    READERS_MAP.put("multipleOf", (element, object, queue) -> {
      if (element instanceof JsonNumberLiteral) object.setMultipleOf(((JsonNumberLiteral)element).getValue());
    });
    READERS_MAP.put("maximum", (element, object, queue) -> {
      if (element instanceof JsonNumberLiteral) object.setMaximum(((JsonNumberLiteral)element).getValue());
    });
    READERS_MAP.put("minimum", (element, object, queue) -> {
      if (element instanceof JsonNumberLiteral) object.setMinimum(((JsonNumberLiteral)element).getValue());
    });
    READERS_MAP.put("exclusiveMaximum", (element, object, queue) -> {
      if (element instanceof JsonBooleanLiteral) object.setExclusiveMaximum(((JsonBooleanLiteral)element).getValue());
      if (element instanceof JsonNumberLiteral) object.setExclusiveMaximumNumber(((JsonNumberLiteral)element).getValue());
    });
    READERS_MAP.put("exclusiveMinimum", (element, object, queue) -> {
      if (element instanceof JsonBooleanLiteral) object.setExclusiveMinimum(((JsonBooleanLiteral)element).getValue());
      if (element instanceof JsonNumberLiteral) object.setExclusiveMinimumNumber(((JsonNumberLiteral)element).getValue());
    });
    READERS_MAP.put("maxLength", (element, object, queue) -> {
      if (element instanceof JsonNumberLiteral) object.setMaxLength((int)((JsonNumberLiteral)element).getValue());
    });
    READERS_MAP.put("minLength", (element, object, queue) -> {
      if (element instanceof JsonNumberLiteral) object.setMinLength((int)((JsonNumberLiteral)element).getValue());
    });
    READERS_MAP.put("pattern", (element, object, queue) -> {
      if (element instanceof JsonStringLiteral) object.setPattern(StringUtil.unquoteString(element.getText()));
    });
    READERS_MAP.put(JsonSchemaObject.ADDITIONAL_ITEMS, createAdditionalItems());
    READERS_MAP.put(JsonSchemaObject.ITEMS, createItems());
    READERS_MAP.put("contains", createContains());
    READERS_MAP.put("maxItems", (element, object, queue) -> {
      if (element instanceof JsonNumberLiteral) object.setMaxItems((int)((JsonNumberLiteral)element).getValue());
    });
    READERS_MAP.put("minItems", (element, object, queue) -> {
      if (element instanceof JsonNumberLiteral) object.setMinItems((int)((JsonNumberLiteral)element).getValue());
    });
    READERS_MAP.put("uniqueItems", (element, object, queue) -> {
      if (element instanceof JsonBooleanLiteral) object.setUniqueItems(((JsonBooleanLiteral)element).getValue());
    });
    READERS_MAP.put("maxProperties", (element, object, queue) -> {
      if (element instanceof JsonNumberLiteral) object.setMaxProperties((int)((JsonNumberLiteral)element).getValue());
    });
    READERS_MAP.put("minProperties", (element, object, queue) -> {
      if (element instanceof JsonNumberLiteral) object.setMinProperties((int)((JsonNumberLiteral)element).getValue());
    });
    READERS_MAP.put("required", createRequired());
    READERS_MAP.put("additionalProperties", createAdditionalProperties());
    READERS_MAP.put("propertyNames", createPropertyNames());
    READERS_MAP.put("patternProperties", createPatternProperties());
    READERS_MAP.put("dependencies", createDependencies());
    READERS_MAP.put("enum", createEnum());
    READERS_MAP.put("const", (element, object, queue) -> {
      if (element instanceof JsonValue) object.setEnum(ContainerUtil.createMaybeSingletonList(readEnumValue((JsonValue)element)));
    });
    READERS_MAP.put("type", createType());
    READERS_MAP.put("allOf", createContainer((object, members) -> object.setAllOf(members)));
    READERS_MAP.put("anyOf", createContainer((object, members) -> object.setAnyOf(members)));
    READERS_MAP.put("oneOf", createContainer((object, members) -> object.setOneOf(members)));
    READERS_MAP.put("not", createNot());
    READERS_MAP.put("if", createIf());
    READERS_MAP.put("then", createThen());
    READERS_MAP.put("else", createElse());
    READERS_MAP.put("instanceof", ((element, object, queue) -> object.shouldValidateAgainstJSType()));
    READERS_MAP.put("typeof", ((element, object, queue) -> object.shouldValidateAgainstJSType()));
  }

  private static MyReader createIf() {
    return (element, object, queue) -> {
      if (element instanceof JsonObject) {
        final JsonSchemaObject ifSchema = new JsonSchemaObject((JsonObject)element);
        queue.add(ifSchema);
        object.setIf(ifSchema);
      }
    };
  }

  private static MyReader createThen() {
    return (element, object, queue) -> {
      if (element instanceof JsonObject) {
        final JsonSchemaObject ifSchema = new JsonSchemaObject((JsonObject)element);
        queue.add(ifSchema);
        object.setThen(ifSchema);
      }
    };
  }

  private static MyReader createElse() {
    return (element, object, queue) -> {
      if (element instanceof JsonObject) {
        final JsonSchemaObject ifSchema = new JsonSchemaObject((JsonObject)element);
        queue.add(ifSchema);
        object.setElse(ifSchema);
      }
    };
  }

  private static MyReader createNot() {
    return (element, object, queue) -> {
      if (element instanceof JsonObject) {
        final JsonSchemaObject not = new JsonSchemaObject((JsonObject)element);
        queue.add(not);
        object.setNot(not);
      }
    };
  }

  private static MyReader createContainer(@NotNull final PairConsumer<JsonSchemaObject, List<JsonSchemaObject>> delegate) {
    return (element, object, queue) -> {
      if (element instanceof JsonArray) {
        final List<JsonValue> list = ((JsonArray)element).getValueList();
        final List<JsonSchemaObject> members = list.stream().filter(el -> el instanceof JsonObject)
          .map(el -> {
            final JsonSchemaObject child = new JsonSchemaObject((JsonObject)el);
            queue.add(child);
            return child;
          }).collect(Collectors.toList());
        delegate.consume(object, members);
      }
    };
  }

  private static MyReader createType() {
    return (element, object, queue) -> {
      if (element instanceof JsonStringLiteral) {
        final JsonSchemaType type = parseType(StringUtil.unquoteString(element.getText()));
        if (type != null) object.setType(type);
      } else if (element instanceof JsonArray) {
        final List<JsonSchemaType> typeList = ((JsonArray)element).getValueList().stream()
          .filter(notEmptyString()).map(el -> parseType(StringUtil.unquoteString(el.getText())))
          .filter(el -> el != null).collect(Collectors.toList());
        if (!typeList.isEmpty()) object.setTypeVariants(typeList);
      }
    };
  }

  @Nullable
  private static JsonSchemaType parseType(@NotNull final String typeString) {
    try {
      return JsonSchemaType.valueOf("_" + typeString);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  @Nullable
  private static Object readEnumValue(JsonValue value) {
    if (value instanceof JsonStringLiteral) {
      return "\"" + StringUtil.unquoteString(((JsonStringLiteral)value).getValue()) + "\"";
    } else if (value instanceof JsonNumberLiteral) {
      return getNumber((JsonNumberLiteral)value);
    } else if (value instanceof JsonBooleanLiteral) {
      return ((JsonBooleanLiteral)value).getValue();
    } else if (value instanceof JsonNullLiteral) {
      return "null";
    }
    return null;
  }

  private static MyReader createEnum() {
    return (element, object, queue) -> {
      if (element instanceof JsonArray) {
        final List<Object> objects = new ArrayList<>();
        final List<JsonValue> list = ((JsonArray)element).getValueList();
        for (JsonValue value : list) {
          Object enumValue = readEnumValue(value);
          if (enumValue != null) objects.add(enumValue);
        }
        object.setEnum(objects);
      }
    };
  }

  @NotNull
  private static Number getNumber(@NotNull JsonNumberLiteral value) {
    Number numberValue;
    try {
      numberValue = Integer.parseInt(value.getText());
    } catch (NumberFormatException e) {
      numberValue = value.getValue();
    }
    return numberValue;
  }

  private static MyReader createDependencies() {
    return (element, object, queue) -> {
      if (element instanceof JsonObject) {
        final HashMap<String, List<String>> propertyDependencies = new HashMap<>();
        final HashMap<String, JsonSchemaObject> schemaDependencies = new HashMap<>();

        final List<JsonProperty> list = ((JsonObject)element).getPropertyList();
        for (JsonProperty property : list) {
          if (StringUtil.isEmptyOrSpaces(property.getName()) || property.getValue() == null) continue;
          if (property.getValue() instanceof JsonArray) {
            final List<String> dependencies = ((JsonArray)property.getValue()).getValueList().stream()
              .filter(notEmptyString())
              .map(el -> StringUtil.unquoteString(el.getText())).collect(Collectors.toList());
            if (!dependencies.isEmpty()) propertyDependencies.put(property.getName(), dependencies);
          } else if (property.getValue() instanceof JsonObject) {
            final JsonSchemaObject child = new JsonSchemaObject((JsonObject)property.getValue());
            queue.add(child);
            schemaDependencies.put(property.getName(), child);
          }
        }

        object.setPropertyDependencies(propertyDependencies);
        object.setSchemaDependencies(schemaDependencies);
      }
    };
  }

  @NotNull
  private static Predicate<JsonValue> notEmptyString() {
    return el -> el instanceof JsonStringLiteral && !StringUtil.isEmptyOrSpaces(el.getText());
  }

  private static MyReader createPatternProperties() {
    return (element, object, queue) -> {
      if (element instanceof JsonObject) {
        object.setPatternProperties(readInnerObject((JsonObject)element, queue));
      }
    };
  }

  private static MyReader createAdditionalProperties() {
    return (element, object, queue) -> {
      if (element instanceof JsonBooleanLiteral) {
        object.setAdditionalPropertiesAllowed(((JsonBooleanLiteral)element).getValue());
      } else if (element instanceof JsonObject) {
        final JsonSchemaObject schema = new JsonSchemaObject((JsonObject)element);
        queue.add(schema);
        object.setAdditionalPropertiesSchema(schema);
      }
    };
  }

  private static MyReader createPropertyNames() {
    return (element, object, queue) -> {
      if (element instanceof JsonObject) {
        final JsonSchemaObject schema = new JsonSchemaObject((JsonObject)element);
        queue.add(schema);
        object.setPropertyNamesSchema(schema);
      }
    };
  }

  private static MyReader createRequired() {
    return (element, object, queue) -> {
      if (element instanceof JsonArray) {
        object.setRequired(((JsonArray)element).getValueList().stream()
                             .filter(notEmptyString())
                             .map(el -> StringUtil.unquoteString(el.getText())).collect(Collectors.toList()));
      }
    };
  }

  private static MyReader createItems() {
    return (element, object, queue) -> {
      if (element instanceof JsonObject) {
        final JsonSchemaObject schema = new JsonSchemaObject((JsonObject)element);
        queue.add(schema);
        object.setItemsSchema(schema);
      } else if (element instanceof JsonArray) {
        final List<JsonSchemaObject> list = new ArrayList<>();
        final List<JsonValue> values = ((JsonArray)element).getValueList();
        for (JsonValue value : values) {
          if (value instanceof JsonObject) {
            final JsonSchemaObject child = new JsonSchemaObject((JsonObject)value);
            queue.add(child);
            list.add(child);
          }
        }
        object.setItemsSchemaList(list);
      }
    };
  }

  private static MyReader createContains() {
    return (element, object, queue) -> {
      if (element instanceof JsonObject) {
        final JsonSchemaObject schema = new JsonSchemaObject((JsonObject)element);
        queue.add(schema);
        object.setContainsSchema(schema);
      }
    };
  }

  private static MyReader createAdditionalItems() {
    return (element, object, queue) -> {
      if (element instanceof JsonBooleanLiteral) {
        object.setAdditionalItemsAllowed(((JsonBooleanLiteral)element).getValue());
      } else if (element instanceof JsonObject) {
        final JsonSchemaObject additionalItemsSchema = new JsonSchemaObject((JsonObject)element);
        queue.add(additionalItemsSchema);
        object.setAdditionalItemsSchema(additionalItemsSchema);
      }
    };
  }

  private static MyReader createPropertiesConsumer() {
    return (element, object, queue) -> {
      if (element instanceof JsonObject) {
        object.setProperties(readInnerObject((JsonObject)element, queue));
      }
    };
  }

  private static MyReader createDefinitionsConsumer() {
    return (element, object, queue) -> {
      if (element instanceof JsonObject) {
        final JsonObject definitions = (JsonObject)element;
        object.setDefinitionsMap(readInnerObject(definitions, queue));
      }
    };
  }

  @NotNull
  private static Map<String, JsonSchemaObject> readInnerObject(@NotNull JsonObject element,
                                                               @NotNull Collection<JsonSchemaObject> queue) {
    final List<JsonProperty> properties = element.getPropertyList();
    final Map<String, JsonSchemaObject> map = new HashMap<>();
    for (JsonProperty property : properties) {
      if (StringUtil.isEmptyOrSpaces(property.getName()) || !(property.getValue() instanceof JsonObject)) continue;
      final JsonSchemaObject child = new JsonSchemaObject((JsonObject)property.getValue());
      queue.add(child);
      map.put(property.getName(), child);
    }
    return map;
  }

  private static MyReader createDefault() {
    return (element, object, queue) -> {
      if (element instanceof JsonObject) {
        final JsonSchemaObject schemaObject = new JsonSchemaObject((JsonObject)element);
        queue.add(schemaObject);
        object.setDefault(schemaObject);
      } else if (element instanceof JsonStringLiteral) {
        object.setDefault(StringUtil.unquoteString(((JsonStringLiteral)element).getValue()));
      } else if (element instanceof JsonNumberLiteral) {
        object.setDefault(getNumber((JsonNumberLiteral) element));
      } else if (element instanceof JsonBooleanLiteral) {
        object.setDefault(((JsonBooleanLiteral)element).getValue());
      }
    };
  }

  private interface MyReader {
    void read(@NotNull JsonElement source, @NotNull JsonSchemaObject target, @NotNull Collection<JsonSchemaObject> processingQueue);
  }
}
