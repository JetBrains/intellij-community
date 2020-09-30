// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;


import com.intellij.json.JsonBundle;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.AstLoadingFilter;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Irina.Chernushina on 1/13/2017.
 */
public class JsonSchemaReader {
  private static final int MAX_SCHEMA_LENGTH = FileUtilRt.LARGE_FOR_CONTENT_LOADING;
  public static final Logger LOG = Logger.getInstance(JsonSchemaReader.class);
  public static final NotificationGroup ERRORS_NOTIFICATION = NotificationGroup.logOnlyGroup("JSON Schema");

  private final Map<String, JsonSchemaObject> myIds = new HashMap<>();
  private final ArrayDeque<Pair<JsonSchemaObject, JsonValueAdapter>> myQueue;

  private static final Map<String, MyReader> READERS_MAP = new HashMap<>();
  static {
    fillMap();
  }

  @Nullable private final VirtualFile myFile;

  public JsonSchemaReader(@Nullable VirtualFile file) {
    myFile = file;
    myQueue = new ArrayDeque<>();
  }

  @NotNull
  public static JsonSchemaObject readFromFile(@NotNull Project project, @NotNull VirtualFile file) throws Exception {
    if (!file.isValid()) {
      throw new Exception(JsonBundle.message("schema.reader.cant.load.file", file.getName()));
    }

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    JsonSchemaObject object = psiFile == null ? null : new JsonSchemaReader(file).read(psiFile);
    if (object == null) {
      throw new Exception(JsonBundle.message("schema.reader.cant.load.model", file.getName()));
    }
    return object;
  }

  @Nullable
  public static @DialogMessage String checkIfValidJsonSchema(@NotNull Project project, @NotNull VirtualFile file) {
    final long length = file.getLength();
    final String fileName = file.getName();
    if (length > MAX_SCHEMA_LENGTH) {
      return JsonBundle.message("schema.reader.file.too.large", fileName, length);
    }
    if (length == 0) {
      return JsonBundle.message("schema.reader.file.empty", fileName);
    }
    try {
      readFromFile(project, file);
    } catch (Exception e) {
      final String message = JsonBundle.message("schema.reader.file.not.found.or.error", fileName, e.getMessage());
      LOG.info(message);
      return message;
    }
    return null;
  }

  private static JsonSchemaObject enqueue(@NotNull Collection<Pair<JsonSchemaObject, JsonValueAdapter>> queue,
                                          @NotNull JsonSchemaObject schemaObject,
                                          @NotNull JsonValueAdapter container) {
    queue.add(Pair.create(schemaObject, container));
    return schemaObject;
  }

  @Nullable
  public JsonSchemaObject read(@NotNull PsiFile file) {
    JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(file, JsonSchemaObject.NULL_OBJ);
    if (walker == null) return null;
    PsiElement root = AstLoadingFilter.forceAllowTreeLoading(file, () -> ContainerUtil.getFirstItem(walker.getRoots(file)));
    return root == null ? null : read(root, walker);
  }

  @Nullable
  private JsonSchemaObject read(@NotNull final PsiElement object, @NotNull JsonLikePsiWalker walker) {
    final JsonSchemaObject root = new JsonSchemaObject(myFile, "/");
    JsonValueAdapter rootAdapter = walker.createValueAdapter(object);
    if (rootAdapter == null) return null;
    enqueue(myQueue, root, rootAdapter);
    while (!myQueue.isEmpty()) {
      final Pair<JsonSchemaObject, JsonValueAdapter> currentItem = myQueue.removeFirst();

      JsonSchemaObject currentSchema = currentItem.first;
      String pointer = currentSchema.getPointer();
      JsonValueAdapter adapter = currentItem.second;

      if (adapter instanceof JsonObjectValueAdapter) {
        final List<JsonPropertyAdapter> list = ((JsonObjectValueAdapter)adapter).getPropertyList();
        for (JsonPropertyAdapter property : list) {
          Collection<JsonValueAdapter> values = property.getValues();
          if (values.size() != 1) continue;
          String name = property.getName();
          if (name == null) continue;
          final MyReader reader = READERS_MAP.get(name);
          JsonValueAdapter value = values.iterator().next();
          if (reader != null) {
            reader.read(value, currentSchema, myQueue, myFile);
          }
          else {
            readSingleDefinition(name, value, currentSchema, pointer);
          }
        }
      }
      else if (adapter instanceof JsonArrayValueAdapter) {
        List<JsonValueAdapter> values = ((JsonArrayValueAdapter)adapter).getElements();
        for (int i = 0; i < values.size(); i++) {
          readSingleDefinition(String.valueOf(i), values.get(i), currentSchema, pointer);
        }
      }

      if (currentSchema.getId() != null) myIds.put(currentSchema.getId(), currentSchema);
      currentSchema.completeInitialization(adapter);
    }
    return root;
  }

  public Map<String, JsonSchemaObject> getIds() {
    return myIds;
  }

  private void readSingleDefinition(@NotNull String name,
                                    @NotNull JsonValueAdapter value,
                                    @NotNull JsonSchemaObject schema,
                                    String pointer) {
    String nextPointer = getNewPointer(name, pointer);
    final JsonSchemaObject defined = enqueue(myQueue, new JsonSchemaObject(myFile, nextPointer), value);
    Map<String, JsonSchemaObject> definitions = schema.getDefinitionsMap();
    if (definitions == null) schema.setDefinitionsMap(definitions = new HashMap<>());
    definitions.put(name, defined);
  }

  @NotNull
  private static String getNewPointer(@NotNull String name, String oldPointer) {
    return oldPointer.equals("/") ? oldPointer + name : oldPointer + "/" + name;
  }

  private static void fillMap() {
    READERS_MAP.put("$anchor", createFromStringValue((object, s) -> object.setId(s)));
    READERS_MAP.put("$id", createFromStringValue((object, s) -> object.setId(s)));
    READERS_MAP.put("id", createFromStringValue((object, s) -> object.setId(s)));
    READERS_MAP.put("$schema", createFromStringValue((object, s) -> object.setSchema(s)));
    READERS_MAP.put("description", createFromStringValue((object, s) -> object.setDescription(s)));
    // non-standard deprecation property used by VSCode
    READERS_MAP.put("deprecationMessage", createFromStringValue((object, s) -> object.setDeprecationMessage(s)));
    READERS_MAP.put(JsonSchemaObject.X_INTELLIJ_HTML_DESCRIPTION, createFromStringValue((object, s) -> object.setHtmlDescription(s)));
    READERS_MAP.put(JsonSchemaObject.X_INTELLIJ_LANGUAGE_INJECTION, (element, object, queue, virtualFile) -> readInjectionMetadata(element, object));
    READERS_MAP.put(JsonSchemaObject.X_INTELLIJ_ENUM_METADATA, (element, object, queue, virtualFile) -> readEnumMetadata(element, object));
    READERS_MAP.put(JsonSchemaObject.X_INTELLIJ_CASE_INSENSITIVE, (element, object, queue, virtualFile) -> {
      if (element.isBooleanLiteral()) object.setForceCaseInsensitive(getBoolean(element));
    });
    READERS_MAP.put("title", createFromStringValue((object, s) -> object.setTitle(s)));
    READERS_MAP.put("$ref", createFromStringValue((object, s) -> object.setRef(s)));
    READERS_MAP.put("$recursiveRef", createFromStringValue((object, s) -> {
      object.setRef(s);
      object.setRefRecursive(true);
    }));
    READERS_MAP.put("$recursiveAnchor", (element, object, queue, virtualFile) -> {
      if (element.isBooleanLiteral()) object.setRecursiveAnchor(true);
    });
    READERS_MAP.put("default", createDefault());
    READERS_MAP.put("format", createFromStringValue((object, s) -> object.setFormat(s)));
    READERS_MAP.put(JsonSchemaObject.DEFINITIONS, createDefinitionsConsumer());
    READERS_MAP.put(JsonSchemaObject.DEFINITIONS_v9, createDefinitionsConsumer());
    READERS_MAP.put(JsonSchemaObject.PROPERTIES, createPropertiesConsumer());
    READERS_MAP.put("multipleOf", createFromNumber((object, i) -> object.setMultipleOf(i)));
    READERS_MAP.put("maximum", createFromNumber((object, i) -> object.setMaximum(i)));
    READERS_MAP.put("minimum", createFromNumber((object, i) -> object.setMinimum(i)));
    READERS_MAP.put("exclusiveMaximum", (element, object, queue, virtualFile) -> {
      if (element.isBooleanLiteral()) object.setExclusiveMaximum(getBoolean(element));
      else if (element.isNumberLiteral()) object.setExclusiveMaximumNumber(getNumber(element));
    });
    READERS_MAP.put("exclusiveMinimum", (element, object, queue, virtualFile) -> {
      if (element.isBooleanLiteral()) object.setExclusiveMinimum(getBoolean(element));
      else if (element.isNumberLiteral()) object.setExclusiveMinimumNumber(getNumber(element));
    });
    READERS_MAP.put("maxLength", createFromInteger((object, i) -> object.setMaxLength(i)));
    READERS_MAP.put("minLength", createFromInteger((object, i) -> object.setMinLength(i)));
    READERS_MAP.put("pattern", createFromStringValue((object, s) -> object.setPattern(s)));
    READERS_MAP.put(JsonSchemaObject.ADDITIONAL_ITEMS, createAdditionalItems());
    READERS_MAP.put(JsonSchemaObject.ITEMS, createItems());
    READERS_MAP.put("contains", createContains());
    READERS_MAP.put("maxItems", createFromInteger((object, i) -> object.setMaxItems(i)));
    READERS_MAP.put("minItems", createFromInteger((object, i) -> object.setMinItems(i)));
    READERS_MAP.put("uniqueItems", (element, object, queue, virtualFile) -> {
      if (element.isBooleanLiteral()) object.setUniqueItems(getBoolean(element));
    });
    READERS_MAP.put("maxProperties", createFromInteger((object, i) -> object.setMaxProperties(i)));
    READERS_MAP.put("minProperties", createFromInteger((object, i) -> object.setMinProperties(i)));
    READERS_MAP.put("required", createRequired());
    READERS_MAP.put("additionalProperties", createAdditionalProperties());
    READERS_MAP.put("propertyNames", createFromObject("propertyNames", (object, schema) -> object.setPropertyNamesSchema(schema)));
    READERS_MAP.put("patternProperties", createPatternProperties());
    READERS_MAP.put("dependencies", createDependencies());
    READERS_MAP.put("enum", createEnum());
    READERS_MAP.put("const", (element, object, queue, virtualFile) -> object.setEnum(ContainerUtil.createMaybeSingletonList(readEnumValue(element))));
    READERS_MAP.put("type", createType());
    READERS_MAP.put("allOf", createContainer((object, members) -> object.setAllOf(members)));
    READERS_MAP.put("anyOf", createContainer((object, members) -> object.setAnyOf(members)));
    READERS_MAP.put("oneOf", createContainer((object, members) -> object.setOneOf(members)));
    READERS_MAP.put("not", createFromObject("not", (object, schema1) -> object.setNot(schema1)));
    READERS_MAP.put("if", createFromObject("if", (object, schema) -> object.setIf(schema)));
    READERS_MAP.put("then", createFromObject("then", (object, schema) -> object.setThen(schema)));
    READERS_MAP.put("else", createFromObject("else", (object, schema) -> object.setElse(schema)));
    READERS_MAP.put("instanceof", ((element, object, queue, virtualFile) -> object.setShouldValidateAgainstJSType()));
    READERS_MAP.put("typeof", ((element, object, queue, virtualFile) -> object.setShouldValidateAgainstJSType()));
  }

  private static void readEnumMetadata(JsonValueAdapter element, JsonSchemaObject object) {
    if (!(element instanceof JsonObjectValueAdapter)) return;
    Map<String, Map<String, String>> metadataMap = new HashMap<>();
    for (JsonPropertyAdapter adapter : ((JsonObjectValueAdapter)element).getPropertyList()) {
      String name = adapter.getName();
      if (name == null) continue;
      Collection<JsonValueAdapter> values = adapter.getValues();
      if (values.size() != 1) continue;
      JsonValueAdapter valueAdapter = values.iterator().next();
      if (valueAdapter.isStringLiteral()) {
        metadataMap.put(name, Collections.singletonMap("description", getString(valueAdapter)));
      }
      else if (valueAdapter instanceof JsonObjectValueAdapter) {
        Map<String, String> valueMap = new HashMap<>();
        for (JsonPropertyAdapter propertyAdapter : ((JsonObjectValueAdapter)valueAdapter).getPropertyList()) {
          String adapterName = propertyAdapter.getName();
          if (adapterName == null) continue;
          Collection<JsonValueAdapter> adapterValues = propertyAdapter.getValues();
          if (adapterValues.size() != 1) continue;
          JsonValueAdapter next = adapterValues.iterator().next();
          if (next.isStringLiteral()) {
            valueMap.put(adapterName, getString(next));
          }
        }
        metadataMap.put(name, valueMap);
      }
    }
    object.setEnumMetadata(metadataMap);
  }

  private static void readInjectionMetadata(JsonValueAdapter element, JsonSchemaObject object) {
    if (element.isStringLiteral()) {
      object.setLanguageInjection(getString(element));
    }
    else if (element instanceof JsonObjectValueAdapter) {
      for (JsonPropertyAdapter adapter : ((JsonObjectValueAdapter)element).getPropertyList()) {
        String lang = readSingleProp(adapter, "language", JsonSchemaReader::getString);
        if (lang != null) object.setLanguageInjection(lang);
        String prefix = readSingleProp(adapter, "prefix", JsonSchemaReader::getString);
        if (prefix != null) object.setLanguageInjectionPrefix(prefix);
        String postfix = readSingleProp(adapter, "suffix", JsonSchemaReader::getString);
        if (postfix != null) object.setLanguageInjectionPostfix(postfix);
      }
    }
  }

  @Nullable
  private static <T> T readSingleProp(JsonPropertyAdapter adapter, String propName, Function<JsonValueAdapter, T> getterFunc) {
    if (propName.equals(adapter.getName())) {
      Collection<JsonValueAdapter> values = adapter.getValues();
      if (values.size() == 1) {
        return getterFunc.apply(values.iterator().next());
      }
    }
    return null;
  }

  private static MyReader createFromStringValue(PairConsumer<JsonSchemaObject, String> propertySetter) {
    return (element, object, queue, virtualFile) -> {
      if (element.isStringLiteral()) {
        propertySetter.consume(object, getString(element));
      }
    };
  }

  private static MyReader createFromInteger(PairConsumer<JsonSchemaObject, Integer> propertySetter) {
    return (element, object, queue, virtualFile) -> {
      if (element.isNumberLiteral()) {
        propertySetter.consume(object, (int)getNumber(element));
      }
    };
  }

  private static MyReader createFromNumber(PairConsumer<JsonSchemaObject, Number> propertySetter) {
    return (element, object, queue, virtualFile) -> {
      if (element.isNumberLiteral()) {
        propertySetter.consume(object, getNumber(element));
      }
    };
  }

  private static MyReader createFromObject(String prop, PairConsumer<JsonSchemaObject, JsonSchemaObject> propertySetter) {
    return (element, object, queue, virtualFile) -> {
      if (element.isObject()) {
        propertySetter.consume(object, enqueue(queue, new JsonSchemaObject(virtualFile, getNewPointer(prop, object.getPointer())), element));
      }
    };
  }

  private static MyReader createContainer(@NotNull final PairConsumer<JsonSchemaObject, List<JsonSchemaObject>> delegate) {
    return (element, object, queue, virtualFile) -> {
      if (element instanceof JsonArrayValueAdapter) {
        final List<JsonValueAdapter> list = ((JsonArrayValueAdapter)element).getElements();
        final List<JsonSchemaObject> members = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
          JsonValueAdapter value = list.get(i);
          if (!(value.isObject())) continue;
          members.add(enqueue(queue, new JsonSchemaObject(virtualFile, getNewPointer(String.valueOf(i), object.getPointer())), value));
        }
        delegate.consume(object, members);
      }
    };
  }

  private static MyReader createType() {
    return (element, object, queue, virtualFile) -> {
      if (element.isStringLiteral()) {
        final JsonSchemaType type = parseType(StringUtil.unquoteString(element.getDelegate().getText()));
        if (type != null) object.setType(type);
      } else if (element instanceof JsonArrayValueAdapter) {
        final Set<JsonSchemaType> typeList = ((JsonArrayValueAdapter)element).getElements().stream()
          .filter(notEmptyString()).map(el -> parseType(StringUtil.unquoteString(el.getDelegate().getText())))
          .filter(el -> el != null).collect(Collectors.toSet());
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
  private static Object readEnumValue(JsonValueAdapter value) {
    if (value.isStringLiteral()) {
      return "\"" + StringUtil.unquoteString(value.getDelegate().getText()) + "\"";
    } else if (value.isNumberLiteral()) {
      return getNumber(value);
    } else if (value.isBooleanLiteral()) {
      return getBoolean(value);
    } else if (value.isNull()) {
      return "null";
    } else if (value instanceof JsonArrayValueAdapter) {
      return new EnumArrayValueWrapper(((JsonArrayValueAdapter)value).getElements().stream().map(v -> readEnumValue(v)).filter(v -> v != null).toArray());
    } else if (value instanceof JsonObjectValueAdapter) {
      return new EnumObjectValueWrapper(((JsonObjectValueAdapter)value).getPropertyList().stream()
        .filter(p -> p.getValues().size() == 1)
        .map(p -> Pair.create(p.getName(), readEnumValue(p.getValues().iterator().next())))
        .filter(p -> p.second != null)
        .collect(Collectors.toMap(p -> p.first, p -> p.second)));
    }
    return null;
  }

  private static MyReader createEnum() {
    return (element, object, queue, virtualFile) -> {
      if (element instanceof JsonArrayValueAdapter) {
        final List<Object> objects = new ArrayList<>();
        final List<JsonValueAdapter> list = ((JsonArrayValueAdapter)element).getElements();
        for (JsonValueAdapter value : list) {
          Object enumValue = readEnumValue(value);
          if (enumValue == null) return; // don't validate if we have unsupported entity kinds
          objects.add(enumValue);
        }
        object.setEnum(objects);
      }
    };
  }

  private static String getString(@NotNull JsonValueAdapter value) {
    return StringUtil.unquoteString(value.getDelegate().getText());
  }

  private static boolean getBoolean(@NotNull JsonValueAdapter value) {
    return Boolean.parseBoolean(value.getDelegate().getText());
  }

  @NotNull
  private static Number getNumber(@NotNull JsonValueAdapter value) {
    Number numberValue;
    try {
      numberValue = Integer.parseInt(value.getDelegate().getText());
    } catch (NumberFormatException e) {
      try {
        numberValue = Double.parseDouble(value.getDelegate().getText());
      }
      catch (NumberFormatException e2) {
        return -1;
      }
    }
    return numberValue;
  }

  private static MyReader createDependencies() {
    return (element, object, queue, virtualFile) -> {
      if (element instanceof JsonObjectValueAdapter) {
        final HashMap<String, List<String>> propertyDependencies = new HashMap<>();
        final HashMap<String, JsonSchemaObject> schemaDependencies = new HashMap<>();

        final List<JsonPropertyAdapter> list = ((JsonObjectValueAdapter)element).getPropertyList();
        for (JsonPropertyAdapter property : list) {
          Collection<JsonValueAdapter> values = property.getValues();
          if (values.size() != 1) continue;
          JsonValueAdapter value = values.iterator().next();
          if (value == null) continue;
          if (value instanceof JsonArrayValueAdapter) {
            final List<String> dependencies = ((JsonArrayValueAdapter)value).getElements().stream()
              .filter(notEmptyString())
              .map(el -> StringUtil.unquoteString(el.getDelegate().getText())).collect(Collectors.toList());
            if (!dependencies.isEmpty()) propertyDependencies.put(property.getName(), dependencies);
          } else if (value.isObject()) {
            String newPointer = getNewPointer("dependencies/" + property.getName(), object.getPointer());
            schemaDependencies.put(property.getName(), enqueue(queue, new JsonSchemaObject(virtualFile, newPointer), value));
          }
        }

        object.setPropertyDependencies(propertyDependencies);
        object.setSchemaDependencies(schemaDependencies);
      }
    };
  }

  @NotNull
  private static Predicate<JsonValueAdapter> notEmptyString() {
    return el -> el.isStringLiteral() && !StringUtil.isEmptyOrSpaces(el.getDelegate().getText());
  }

  private static MyReader createPatternProperties() {
    return (element, object, queue, virtualFile) -> {
      if (element.isObject()) {
        object.setPatternProperties(readInnerObject(getNewPointer("patternProperties", object.getPointer()), element, queue, virtualFile));
      }
    };
  }

  private static MyReader createAdditionalProperties() {
    return (element, object, queue, virtualFile) -> {
      if (element.isBooleanLiteral()) {
        object.setAdditionalPropertiesAllowed(getBoolean(element));
      } else if (element.isObject()) {
        object.setAdditionalPropertiesSchema(enqueue(queue, new JsonSchemaObject(virtualFile,
                                                                                 getNewPointer("additionalProperties", object.getPointer())),
                                                     element));
      }
    };
  }

  private static MyReader createRequired() {
    return (element, object, queue, virtualFile) -> {
      if (element instanceof JsonArrayValueAdapter) {
        object.setRequired(new LinkedHashSet<>(((JsonArrayValueAdapter)element).getElements().stream()
                                                 .filter(notEmptyString())
                                                 .map(el -> StringUtil.unquoteString(el.getDelegate().getText()))
                                                 .collect(Collectors.toList())));
      }
    };
  }

  private static MyReader createItems() {
    return (element, object, queue, virtualFile) -> {
      if (element.isObject()) {
        object.setItemsSchema(enqueue(queue, new JsonSchemaObject(virtualFile, getNewPointer("items", object.getPointer())), element));
      } else if (element instanceof JsonArrayValueAdapter) {
        final List<JsonSchemaObject> list = new ArrayList<>();
        final List<JsonValueAdapter> values = ((JsonArrayValueAdapter)element).getElements();
        for (int i = 0; i < values.size(); i++) {
          JsonValueAdapter value = values.get(i);
          if (value.isObject()) {
            list.add(enqueue(queue, new JsonSchemaObject(virtualFile, getNewPointer("items/"+i, object.getPointer())), value));
          }
        }
        object.setItemsSchemaList(list);
      }
    };
  }

  private static MyReader createDefinitionsConsumer() {
    return (element, object, queue, virtualFile) -> {
      if (element.isObject()) {
        object.setDefinitionsMap(readInnerObject(getNewPointer("definitions", object.getPointer()), element, queue, virtualFile));
      }
    };
  }

  private static MyReader createContains() {
    return (element, object, queue, virtualFile) -> {
      if (element.isObject()) {
        object.setContainsSchema(enqueue(queue, new JsonSchemaObject(virtualFile, getNewPointer("contains", object.getPointer())), element));
      }
    };
  }

  private static MyReader createAdditionalItems() {
    return (element, object, queue, virtualFile) -> {
      if (element.isBooleanLiteral()) {
        object.setAdditionalItemsAllowed(getBoolean(element));
      } else if (element.isObject()) {
        object.setAdditionalItemsSchema(enqueue(queue, new JsonSchemaObject(virtualFile,
                                                                            getNewPointer("additionalItems", object.getPointer())), element));
      }
    };
  }

  private static MyReader createPropertiesConsumer() {
    return (element, object, queue, virtualFile) -> {
      if (element.isObject()) {
        object.setProperties(readInnerObject(getNewPointer("properties", object.getPointer()), element, queue, virtualFile));
      }
    };
  }

  @NotNull
  private static Map<String, JsonSchemaObject> readInnerObject(String parentPointer, @NotNull JsonValueAdapter element,
                                                               @NotNull Collection<Pair<JsonSchemaObject, JsonValueAdapter>> queue,
                                                               VirtualFile virtualFile) {
    final Map<String, JsonSchemaObject> map = new HashMap<>();
    if (!(element instanceof JsonObjectValueAdapter)) return map;
    final List<JsonPropertyAdapter> properties = ((JsonObjectValueAdapter)element).getPropertyList();
    for (JsonPropertyAdapter property : properties) {
      Collection<JsonValueAdapter> values = property.getValues();
      if (values.size() != 1) continue;
      JsonValueAdapter value = values.iterator().next();
      String propertyName = property.getName();
      if (propertyName == null) continue;
      if (value.isBooleanLiteral()) {
        // schema v7: `propName: true` is equivalent to `propName: {}`
        map.put(propertyName, new JsonSchemaObject(virtualFile, getNewPointer(propertyName, parentPointer)));
        continue;
      }
      if (!value.isObject()) continue;
      map.put(propertyName, enqueue(queue, new JsonSchemaObject(virtualFile, getNewPointer(propertyName, parentPointer)), value));
    }
    return map;
  }

  private static MyReader createDefault() {
    return (element, object, queue, virtualFile) -> {
      if (element.isObject()) {
        object.setDefault(enqueue(queue, new JsonSchemaObject(virtualFile, getNewPointer("default", object.getPointer())), element));
      } else if (element.isStringLiteral()) {
        object.setDefault(StringUtil.unquoteString(element.getDelegate().getText()));
      } else if (element.isNumberLiteral()) {
        object.setDefault(getNumber(element));
      } else if (element.isBooleanLiteral()) {
        object.setDefault(getBoolean(element));
      }
    };
  }

  private interface MyReader {
    void read(@NotNull JsonValueAdapter source,
              @NotNull JsonSchemaObject target,
              @NotNull Collection<Pair<JsonSchemaObject, JsonValueAdapter>> processingQueue,
              @Nullable VirtualFile file);
  }
}
