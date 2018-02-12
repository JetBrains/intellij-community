/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.*;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairConsumer;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
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

  @Nullable
  public static String readSchemaId(@NotNull final Project project, @NotNull final VirtualFile schemaFile) {
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(schemaFile);
    if (!(psiFile instanceof JsonFile)) return null;

    final CachedValueProvider<String> provider = () -> {
      final JsonObject topLevelValue = ObjectUtils.tryCast(((JsonFile)psiFile).getTopLevelValue(), JsonObject.class);
      if (topLevelValue == null) return null;
      return CachedValueProvider.Result.create(readId(topLevelValue), psiFile);
    };
    return ReadAction.compute(() -> CachedValuesManager.getCachedValue(psiFile, provider));
  }

  public JsonSchemaObject read(@NotNull final JsonObject object) {
    final JsonSchemaObject root = new JsonSchemaObject(object);
    myQueue.add(root);
    while (!myQueue.isEmpty()) {
      final JsonSchemaObject currentSchema = myQueue.removeFirst();

      final JsonObject jsonObject = currentSchema.getJsonObject();
      final List<JsonProperty> list = jsonObject.getPropertyList();
      for (JsonProperty property : list) {
        if (StringUtil.isEmptyOrSpaces(property.getName()) || property.getValue() == null) continue;
        final MyReader reader = READERS_MAP.get(property.getName());
        if (reader != null) reader.read(property.getValue(), currentSchema, myQueue);
        else readSingleDefinition(property.getName(), property.getValue(), currentSchema);
      }

      if (currentSchema.getId() != null) myIds.put(currentSchema.getId(), currentSchema);
    }
    return root;
  }

  public Map<String, JsonSchemaObject> getIds() {
    return myIds;
  }

  @Nullable
  private static String readId(@NotNull final JsonObject object) {
    final JsonProperty property = object.findProperty("id");
    if (property != null && property.getValue() instanceof JsonStringLiteral) {
      return JsonSchemaService.normalizeId(StringUtil.unquoteString(property.getValue().getText()));
    }
    return null;
  }

  private void readSingleDefinition(@NotNull String name, @NotNull JsonValue value, @NotNull JsonSchemaObject schema) {
    if (value instanceof JsonObject) {
      final JsonSchemaObject defined = new JsonSchemaObject((JsonObject)value);
      myQueue.add(defined);
      Map<String, JsonSchemaObject> definitions = schema.getDefinitionsMap();
      if (definitions == null) schema.setDefinitionsMap(definitions = new HashMap<>());
      definitions.put(name, defined);
    }
  }

  private static void fillMap() {
    READERS_MAP.put("id", (element, object, queue) -> {
      if (element instanceof JsonStringLiteral) object.setId(StringUtil.unquoteString(element.getText()));
    });
    READERS_MAP.put("$schema", (element, object, queue) -> {
      if (element instanceof JsonStringLiteral) object.setSchema(StringUtil.unquoteString(element.getText()));
    });
    READERS_MAP.put("description", (element, object, queue) -> {
      if (element instanceof JsonStringLiteral) object.setDescription(StringUtil.unquoteString(element.getText()));
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
    });
    READERS_MAP.put("exclusiveMinimum", (element, object, queue) -> {
      if (element instanceof JsonBooleanLiteral) object.setExclusiveMinimum(((JsonBooleanLiteral)element).getValue());
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
    READERS_MAP.put("additionalItems", createAdditionalItems());
    READERS_MAP.put("items", createItems());
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
    READERS_MAP.put("patternProperties", createPatternProperties());
    READERS_MAP.put("dependencies", createDependencies());
    READERS_MAP.put("enum", createEnum());
    READERS_MAP.put("type", createType());
    READERS_MAP.put("allOf", createContainer((object, members) -> object.setAllOf(members)));
    READERS_MAP.put("anyOf", createContainer((object, members) -> object.setAnyOf(members)));
    READERS_MAP.put("oneOf", createContainer((object, members) -> object.setOneOf(members)));
    READERS_MAP.put("not", createNot());
    READERS_MAP.put("instanceof", ((element, object, queue) -> object.shouldValidateAgainstJSType()));
    READERS_MAP.put("typeof", ((element, object, queue) -> object.shouldValidateAgainstJSType()));
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


  private static MyReader createEnum() {
    return (element, object, queue) -> {
      if (element instanceof JsonArray) {
        final List<Object> objects = new ArrayList<>();
        final List<JsonValue> list = ((JsonArray)element).getValueList();
        for (JsonValue value : list) {
          if (value instanceof JsonStringLiteral) {
            objects.add("\"" + StringUtil.unquoteString(((JsonStringLiteral)value).getValue()) + "\"");
          } else if (value instanceof JsonNumberLiteral) {
            objects.add(getNumber((JsonNumberLiteral)value));
          } else if (value instanceof JsonBooleanLiteral) {
            objects.add(((JsonBooleanLiteral)value).getValue());
          } else if (value instanceof JsonNullLiteral) {
            objects.add("null");
          }
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
