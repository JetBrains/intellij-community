// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.impl;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.intellij.json.psi.JsonContainer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * @author Irina.Chernushina on 8/28/2015.
 */
public class JsonSchemaObject {
  @NonNls public static final String DEFINITIONS = "definitions";
  @NonNls public static final String PROPERTIES = "properties";
  @NonNls public static final String ITEMS = "items";
  @NonNls public static final String ADDITIONAL_ITEMS = "additionalItems";
  @NonNls public static final String X_INTELLIJ_HTML_DESCRIPTION = "x-intellij-html-description";
  @NotNull private final JsonContainer myJsonObject;
  @Nullable private Map<String, JsonSchemaObject> myDefinitionsMap;
  @NotNull private Map<String, JsonSchemaObject> myProperties;

  @Nullable private PatternProperties myPatternProperties;
  @Nullable private PropertyNamePattern myPattern;

  @Nullable private String myId;
  @Nullable private String mySchema;

  @Nullable private String myTitle;
  @Nullable private String myDescription;
  @Nullable private String myHtmlDescription;

  @Nullable private JsonSchemaType myType;
  @Nullable private Object myDefault;
  @Nullable private String myRef;
  @Nullable private String myFormat;
  @Nullable private List<JsonSchemaType> myTypeVariants;
  @Nullable private Number myMultipleOf;
  @Nullable private Number myMaximum;
  private boolean myExclusiveMaximum;
  @Nullable private Number myExclusiveMaximumNumber;
  @Nullable private Number myMinimum;
  private boolean myExclusiveMinimum;
  @Nullable private Number myExclusiveMinimumNumber;
  @Nullable private Integer myMaxLength;
  @Nullable private Integer myMinLength;

  @Nullable private Boolean myAdditionalPropertiesAllowed;
  @Nullable private JsonSchemaObject myAdditionalPropertiesSchema;
  @Nullable private JsonSchemaObject myPropertyNamesSchema;

  @Nullable private Boolean myAdditionalItemsAllowed;
  @Nullable private JsonSchemaObject myAdditionalItemsSchema;

  @Nullable private JsonSchemaObject myItemsSchema;
  @Nullable private JsonSchemaObject myContainsSchema;
  @Nullable private List<JsonSchemaObject> myItemsSchemaList;

  @Nullable private Integer myMaxItems;
  @Nullable private Integer myMinItems;

  @Nullable private Boolean myUniqueItems;

  @Nullable private Integer myMaxProperties;
  @Nullable private Integer myMinProperties;
  @Nullable private List<String> myRequired;

  @Nullable private Map<String, List<String>> myPropertyDependencies;
  @Nullable private Map<String, JsonSchemaObject> mySchemaDependencies;

  @Nullable private List<Object> myEnum;

  @Nullable private List<JsonSchemaObject> myAllOf;
  @Nullable private List<JsonSchemaObject> myAnyOf;
  @Nullable private List<JsonSchemaObject> myOneOf;
  @Nullable private JsonSchemaObject myNot;
  @Nullable private JsonSchemaObject myIf;
  @Nullable private JsonSchemaObject myThen;
  @Nullable private JsonSchemaObject myElse;
  private boolean myShouldValidateAgainstJSType;

  public JsonSchemaObject(@NotNull JsonContainer object) {
    myJsonObject = object;
    myProperties = new HashMap<>();
  }

  // peer pointer is not merged!
  public void mergeValues(@NotNull JsonSchemaObject other) {
    // we do not copy id, schema
    mergeProperties(this, other);
    myDefinitionsMap = copyMap(myDefinitionsMap, other.myDefinitionsMap);
    final Map<String, JsonSchemaObject> map = copyMap(myPatternProperties == null ? null : myPatternProperties.mySchemasMap,
                                                      other.myPatternProperties == null ? null : other.myPatternProperties.mySchemasMap);
    myPatternProperties = map == null ? null : new PatternProperties(map);

    if (!StringUtil.isEmptyOrSpaces(other.myTitle)) {
      myTitle = other.myTitle;
    }
    if (!StringUtil.isEmptyOrSpaces(other.myDescription)) {
      myDescription = other.myDescription;
    }
    if (!StringUtil.isEmptyOrSpaces(other.myHtmlDescription)) {
      myHtmlDescription = other.myHtmlDescription;
    }

    if (other.myType != null) myType = other.myType;
    if (other.myDefault != null) myDefault = other.myDefault;
    if (other.myRef != null) myRef = other.myRef;
    if (other.myFormat != null) myFormat = other.myFormat;
    myTypeVariants = copyList(myTypeVariants, other.myTypeVariants);
    if (other.myMultipleOf != null) myMultipleOf = other.myMultipleOf;
    if (other.myMaximum != null) myMaximum = other.myMaximum;
    if (other.myExclusiveMaximumNumber != null) myExclusiveMaximumNumber = other.myExclusiveMaximumNumber;
    myExclusiveMaximum |= other.myExclusiveMaximum;
    if (other.myMinimum != null) myMinimum = other.myMinimum;
    if (other.myExclusiveMinimumNumber != null) myExclusiveMinimumNumber = other.myExclusiveMinimumNumber;
    myExclusiveMinimum |= other.myExclusiveMinimum;
    if (other.myMaxLength != null) myMaxLength = other.myMaxLength;
    if (other.myMinLength != null) myMinLength = other.myMinLength;
    if (other.myPattern != null) myPattern = other.myPattern;
    if (other.myAdditionalPropertiesAllowed != null) myAdditionalPropertiesAllowed = other.myAdditionalPropertiesAllowed;
    if (other.myAdditionalPropertiesSchema != null) myAdditionalPropertiesSchema = other.myAdditionalPropertiesSchema;
    if (other.myPropertyNamesSchema != null) myPropertyNamesSchema = other.myPropertyNamesSchema;
    if (other.myAdditionalItemsAllowed != null) myAdditionalItemsAllowed = other.myAdditionalItemsAllowed;
    if (other.myAdditionalItemsSchema != null) myAdditionalItemsSchema = other.myAdditionalItemsSchema;
    if (other.myItemsSchema != null) myItemsSchema = other.myItemsSchema;
    if (other.myContainsSchema != null) myContainsSchema = other.myContainsSchema;
    myItemsSchemaList = copyList(myItemsSchemaList, other.myItemsSchemaList);
    if (other.myMaxItems != null) myMaxItems = other.myMaxItems;
    if (other.myMinItems != null) myMinItems = other.myMinItems;
    if (other.myUniqueItems != null) myUniqueItems = other.myUniqueItems;
    if (other.myMaxProperties != null) myMaxProperties = other.myMaxProperties;
    if (other.myMinProperties != null) myMinProperties = other.myMinProperties;
    myRequired = copyList(myRequired, other.myRequired);
    myPropertyDependencies = copyMap(myPropertyDependencies, other.myPropertyDependencies);
    mySchemaDependencies = copyMap(mySchemaDependencies, other.mySchemaDependencies);
    if (other.myEnum != null) myEnum = other.myEnum;
    myAllOf = copyList(myAllOf, other.myAllOf);
    myAnyOf = copyList(myAnyOf, other.myAnyOf);
    myOneOf = copyList(myOneOf, other.myOneOf);
    if (other.myNot != null) myNot = other.myNot;
    if (other.myIf != null) myIf = other.myIf;
    if (other.myThen != null) myThen = other.myThen;
    if (other.myElse != null) myElse = other.myElse;
    myShouldValidateAgainstJSType |= other.myShouldValidateAgainstJSType;
  }

  private static void mergeProperties(@NotNull JsonSchemaObject thisObject, @NotNull JsonSchemaObject otherObject) {
    for (Map.Entry<String, JsonSchemaObject> prop: otherObject.myProperties.entrySet()) {
      String key = prop.getKey();
      JsonSchemaObject otherProp = prop.getValue();
      if (!thisObject.myProperties.containsKey(key)) {
        thisObject.myProperties.put(key, otherProp);
      }
      else {
        JsonSchemaObject existingProp = thisObject.myProperties.get(key);
        thisObject.myProperties.put(key, JsonSchemaVariantsTreeBuilder.merge(existingProp, otherProp, otherProp));
      }
    }
  }

  public void shouldValidateAgainstJSType() {
    myShouldValidateAgainstJSType = true;
  }

  public boolean isShouldValidateAgainstJSType() {
    return myShouldValidateAgainstJSType;
  }

  @Nullable
  private static <T> List<T> copyList(@Nullable List<T> target, @Nullable List<T> source) {
    if (source == null || source.isEmpty()) return target;
    if (target == null) target = new ArrayList<>();
    target.addAll(source);
    return target;
  }

  @Nullable
  private static <K, V> Map<K, V> copyMap(@Nullable Map<K, V> target, @Nullable Map<K, V> source) {
    if (source == null || source.isEmpty()) return target;
    if (target == null) target = new HashMap<>();
    target.putAll(source);
    return target;
  }

  @NotNull
  public VirtualFile getSchemaFile() {
    return myJsonObject.getContainingFile().getViewProvider().getVirtualFile();
  }

  @NotNull
  public JsonContainer getJsonObject() {
    return myJsonObject;
  }

  @Nullable
  public Map<String, JsonSchemaObject> getDefinitionsMap() {
    return myDefinitionsMap;
  }

  public void setDefinitionsMap(@NotNull Map<String, JsonSchemaObject> definitionsMap) {
    myDefinitionsMap = definitionsMap;
  }

  @NotNull
  public Map<String, JsonSchemaObject> getProperties() {
    return myProperties;
  }

  public void setProperties(@NotNull Map<String, JsonSchemaObject> properties) {
    myProperties = properties;
  }

  public boolean hasPatternProperties() {
    return myPatternProperties != null;
  }

  public void setPatternProperties(@NotNull Map<String, JsonSchemaObject> patternProperties) {
    myPatternProperties = new PatternProperties(patternProperties);
  }

  @Nullable
  public JsonSchemaType getType() {
    return myType;
  }

  public void setType(@Nullable JsonSchemaType type) {
    myType = type;
  }

  @Nullable
  public Number getMultipleOf() {
    return myMultipleOf;
  }

  public void setMultipleOf(@Nullable Number multipleOf) {
    myMultipleOf = multipleOf;
  }

  @Nullable
  public Number getMaximum() {
    return myMaximum;
  }

  public void setMaximum(@Nullable Number maximum) {
    myMaximum = maximum;
  }

  public boolean isExclusiveMaximum() {
    return myExclusiveMaximum;
  }

  @Nullable
  public Number getExclusiveMaximumNumber() {
    return myExclusiveMaximumNumber;
  }

  public void setExclusiveMaximumNumber(@Nullable Number exclusiveMaximumNumber) {
    myExclusiveMaximumNumber = exclusiveMaximumNumber;
  }

  @Nullable
  public Number getExclusiveMinimumNumber() {
    return myExclusiveMinimumNumber;
  }

  public void setExclusiveMinimumNumber(@Nullable Number exclusiveMinimumNumber) {
    myExclusiveMinimumNumber = exclusiveMinimumNumber;
  }

  public void setExclusiveMaximum(boolean exclusiveMaximum) {
    myExclusiveMaximum = exclusiveMaximum;
  }

  @Nullable
  public Number getMinimum() {
    return myMinimum;
  }

  public void setMinimum(@Nullable Number minimum) {
    myMinimum = minimum;
  }

  public boolean isExclusiveMinimum() {
    return myExclusiveMinimum;
  }

  public void setExclusiveMinimum(boolean exclusiveMinimum) {
    myExclusiveMinimum = exclusiveMinimum;
  }

  @Nullable
  public Integer getMaxLength() {
    return myMaxLength;
  }

  public void setMaxLength(@Nullable Integer maxLength) {
    myMaxLength = maxLength;
  }

  @Nullable
  public Integer getMinLength() {
    return myMinLength;
  }

  public void setMinLength(@Nullable Integer minLength) {
    myMinLength = minLength;
  }

  @Nullable
  public String getPattern() {
    return myPattern == null ? null : myPattern.getPattern();
  }

  public void setPattern(@Nullable String pattern) {
    myPattern = pattern == null ? null : new PropertyNamePattern(pattern);
  }

  @Nullable
  public Boolean getAdditionalPropertiesAllowed() {
    return myAdditionalPropertiesAllowed == null || myAdditionalPropertiesAllowed;
  }

  public void setAdditionalPropertiesAllowed(@Nullable Boolean additionalPropertiesAllowed) {
    myAdditionalPropertiesAllowed = additionalPropertiesAllowed;
  }

  @Nullable
  public JsonSchemaObject getPropertyNamesSchema() {
    return myPropertyNamesSchema;
  }

  public void setPropertyNamesSchema(@Nullable JsonSchemaObject propertyNamesSchema) {
    myPropertyNamesSchema = propertyNamesSchema;
  }

  @Nullable
  public JsonSchemaObject getAdditionalPropertiesSchema() {
    return myAdditionalPropertiesSchema;
  }

  public void setAdditionalPropertiesSchema(@Nullable JsonSchemaObject additionalPropertiesSchema) {
    myAdditionalPropertiesSchema = additionalPropertiesSchema;
  }

  @Nullable
  public Boolean getAdditionalItemsAllowed() {
    return myAdditionalItemsAllowed == null || myAdditionalItemsAllowed;
  }

  public void setAdditionalItemsAllowed(@Nullable Boolean additionalItemsAllowed) {
    myAdditionalItemsAllowed = additionalItemsAllowed;
  }

  @Nullable
  public JsonSchemaObject getAdditionalItemsSchema() {
    return myAdditionalItemsSchema;
  }

  public void setAdditionalItemsSchema(@Nullable JsonSchemaObject additionalItemsSchema) {
    myAdditionalItemsSchema = additionalItemsSchema;
  }

  @Nullable
  public JsonSchemaObject getItemsSchema() {
    return myItemsSchema;
  }

  public void setItemsSchema(@Nullable JsonSchemaObject itemsSchema) {
    myItemsSchema = itemsSchema;
  }

  @Nullable
  public JsonSchemaObject getContainsSchema() {
    return myContainsSchema;
  }

  public void setContainsSchema(@Nullable JsonSchemaObject containsSchema) {
    myContainsSchema = containsSchema;
  }

  @Nullable
  public List<JsonSchemaObject> getItemsSchemaList() {
    return myItemsSchemaList;
  }

  public void setItemsSchemaList(@Nullable List<JsonSchemaObject> itemsSchemaList) {
    myItemsSchemaList = itemsSchemaList;
  }

  @Nullable
  public Integer getMaxItems() {
    return myMaxItems;
  }

  public void setMaxItems(@Nullable Integer maxItems) {
    myMaxItems = maxItems;
  }

  @Nullable
  public Integer getMinItems() {
    return myMinItems;
  }

  public void setMinItems(@Nullable Integer minItems) {
    myMinItems = minItems;
  }

  public boolean isUniqueItems() {
    return Boolean.TRUE.equals(myUniqueItems);
  }

  public void setUniqueItems(boolean uniqueItems) {
    myUniqueItems = uniqueItems;
  }

  @Nullable
  public Integer getMaxProperties() {
    return myMaxProperties;
  }

  public void setMaxProperties(@Nullable Integer maxProperties) {
    myMaxProperties = maxProperties;
  }

  @Nullable
  public Integer getMinProperties() {
    return myMinProperties;
  }

  public void setMinProperties(@Nullable Integer minProperties) {
    myMinProperties = minProperties;
  }

  @Nullable
  public List<String> getRequired() {
    return myRequired;
  }

  public void setRequired(@Nullable List<String> required) {
    myRequired = required;
  }

  @Nullable
  public Map<String, List<String>> getPropertyDependencies() {
    return myPropertyDependencies;
  }

  public void setPropertyDependencies(@Nullable Map<String, List<String>> propertyDependencies) {
    myPropertyDependencies = propertyDependencies;
  }

  @Nullable
  public Map<String, JsonSchemaObject> getSchemaDependencies() {
    return mySchemaDependencies;
  }

  public void setSchemaDependencies(@Nullable Map<String, JsonSchemaObject> schemaDependencies) {
    mySchemaDependencies = schemaDependencies;
  }

  @Nullable
  public List<Object> getEnum() {
    return myEnum;
  }

  public void setEnum(@Nullable List<Object> anEnum) {
    myEnum = anEnum;
  }

  @Nullable
  public List<JsonSchemaObject> getAllOf() {
    return myAllOf;
  }

  public void setAllOf(@Nullable List<JsonSchemaObject> allOf) {
    myAllOf = allOf;
  }

  @Nullable
  public List<JsonSchemaObject> getAnyOf() {
    return myAnyOf;
  }

  public void setAnyOf(@Nullable List<JsonSchemaObject> anyOf) {
    myAnyOf = anyOf;
  }

  @Nullable
  public List<JsonSchemaObject> getOneOf() {
    return myOneOf;
  }

  public void setOneOf(@Nullable List<JsonSchemaObject> oneOf) {
    myOneOf = oneOf;
  }

  @Nullable
  public JsonSchemaObject getNot() {
    return myNot;
  }

  public void setNot(@Nullable JsonSchemaObject not) {
    myNot = not;
  }

  @Nullable
  public JsonSchemaObject getIf() {
    return myIf;
  }

  public void setIf(@Nullable JsonSchemaObject anIf) {
    myIf = anIf;
  }

  @Nullable
  public JsonSchemaObject getThen() {
    return myThen;
  }

  public void setThen(@Nullable JsonSchemaObject then) {
    myThen = then;
  }

  @Nullable
  public JsonSchemaObject getElse() {
    return myElse;
  }

  public void setElse(@Nullable JsonSchemaObject anElse) {
    myElse = anElse;
  }

  @Nullable
  public List<JsonSchemaType> getTypeVariants() {
    return myTypeVariants;
  }

  public void setTypeVariants(@Nullable List<JsonSchemaType> typeVariants) {
    myTypeVariants = typeVariants;
  }

  @Nullable
  public String getRef() {
    return myRef;
  }

  public void setRef(@Nullable String ref) {
    myRef = ref;
  }

  @Nullable
  public Object getDefault() {
    if (JsonSchemaType._integer.equals(myType)) return myDefault instanceof Number ? ((Number)myDefault).intValue() : myDefault;
    return myDefault;
  }

  public void setDefault(@Nullable Object aDefault) {
    myDefault = aDefault;
  }

  @Nullable
  public String getFormat() {
    return myFormat;
  }

  public void setFormat(@Nullable String format) {
    myFormat = format;
  }

  @Nullable
  public String getId() {
    return myId;
  }

  public void setId(@Nullable String id) {
    myId = id;
  }

  @Nullable
  public String getSchema() {
    return mySchema;
  }

  public void setSchema(@Nullable String schema) {
    mySchema = schema;
  }

  @Nullable
  public String getDescription() {
    return myDescription;
  }

  public void setDescription(@NotNull String description) {
    myDescription = unescapeJsonString(description);
  }

  @Nullable
  public String getHtmlDescription() {
    return myHtmlDescription;
  }

  public void setHtmlDescription(@NotNull String htmlDescription) {
    myHtmlDescription = unescapeJsonString(htmlDescription);
  }

  @Nullable
  public String getTitle() {
    return myTitle;
  }

  public void setTitle(@NotNull String title) {
    myTitle = unescapeJsonString(title);
  }

  private static String unescapeJsonString(@NotNull final String text) {
    try {
      final String object = String.format("{\"prop\": \"%s\"}", text);
      return new Gson().fromJson(object, com.google.gson.JsonObject.class).get("prop").getAsString();
    } catch (JsonParseException e) {
      return text;
    }
  }

  @Nullable
  public JsonSchemaObject getMatchingPatternPropertySchema(@NotNull String name) {
    if (myPatternProperties == null) return null;
    return myPatternProperties.getPatternPropertySchema(name);
  }

  public boolean checkByPattern(@NotNull String value) {
    return myPattern != null && myPattern.checkByPattern(value);
  }

  @Nullable
  public String getPatternError() {
    return myPattern == null ? null : myPattern.getPatternError();
  }

  @Nullable
  public Map<JsonContainer, String> getInvalidPatternProperties() {
    if (myPatternProperties != null) {
      final Map<String, String> patterns = myPatternProperties.getInvalidPatterns();

      return patterns.entrySet().stream().map(entry -> {
        final JsonSchemaObject object = myPatternProperties.getSchemaForPattern(entry.getKey());
        assert object != null;
        return Pair.create(object.getJsonObject(), entry.getValue());
      }).collect(Collectors.toMap(o -> o.getFirst(), o -> o.getSecond()));
    }
    return null;
  }

  @Nullable
  public JsonSchemaObject findRelativeDefinition(@NotNull String ref) {
    if ("#".equals(ref) || StringUtil.isEmpty(ref)) {
      return this;
    }
    if (!ref.startsWith("#/")) {
      return null;
    }
    ref = ref.substring(2);
    final List<String> parts = StringUtil.split(ref, "/");
    JsonSchemaObject current = this;
    for (int i = 0; i < parts.size(); i++) {
      if (current == null) return null;
      final String part = parts.get(i);
      if (DEFINITIONS.equals(part)) {
        if (i == (parts.size() - 1)) return null;
        //noinspection AssignmentToForLoopParameter
        final String nextPart = parts.get(++i);
        current = current.getDefinitionsMap() == null ? null : current.getDefinitionsMap().get(nextPart);
        continue;
      }
      if (PROPERTIES.equals(part)) {
        if (i == (parts.size() - 1)) return null;
        //noinspection AssignmentToForLoopParameter
        current = current.getProperties().get(parts.get(++i));
        continue;
      }
      if (ITEMS.equals(part)) {
        if (i == (parts.size() - 1)) {
          current = current.getItemsSchema();
        }
        else {
          //noinspection AssignmentToForLoopParameter
          Integer next = tryParseInt(parts.get(++i));
          List<JsonSchemaObject> itemsSchemaList = current.getItemsSchemaList();
          if (itemsSchemaList != null && next != null && next < itemsSchemaList.size()) {
            current = itemsSchemaList.get(next);
          }
        }
        continue;
      }
      if (ADDITIONAL_ITEMS.equals(part)) {
        if (i == (parts.size() - 1)) {
          current = current.getAdditionalItemsSchema();
        }
        continue;
      }
      
      current = current.getDefinitionsMap() == null ? null : current.getDefinitionsMap().get(part);
    }
    return current;
  }

  @Nullable
  private static Integer tryParseInt(String s) {
    try {
      return Integer.parseInt(s);
    }
    catch (Exception __) {
      return null;
    }
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    JsonSchemaObject object = (JsonSchemaObject)o;

    return myJsonObject.equals(object.myJsonObject);
  }

  @Override
  public int hashCode() {
    return myJsonObject.hashCode();
  }

  @NotNull
  private static String adaptSchemaPattern(String pattern) {
    pattern = pattern.startsWith("^") || pattern.startsWith("*") || pattern.startsWith(".") ? pattern : (".*" + pattern);
    pattern = pattern.endsWith("+") || pattern.endsWith("*") || pattern.endsWith("$") ? pattern : (pattern + ".*");
    pattern = pattern.replace("\\\\", "\\");
    return pattern;
  }


  private static Pair<Pattern, String> compilePattern(@NotNull final String pattern) {
    try {
      return Pair.create(Pattern.compile(adaptSchemaPattern(pattern)), null);
    } catch (PatternSyntaxException e) {
      return Pair.create(null, e.getMessage());
    }
  }

  public static boolean matchPattern(@NotNull final Pattern pattern, @NotNull final String s) {
    Logger.getInstance(JsonSchemaObject.class).info("Pattern: " + pattern.pattern() + ", path: " + s);
    try {
      return pattern.matcher(StringUtil.newBombedCharSequence(s, 300)).matches();
    } catch (ProcessCanceledException e) {
      // something wrong with the pattern, infinite cycle?
      Logger.getInstance(JsonSchemaObject.class).info("Pattern matching canceled");
      return false;
    } catch (Exception e) {
      // catch exceptions around to prevent things like:
      // https://bugs.openjdk.java.net/browse/JDK-6984178
      Logger.getInstance(JsonSchemaObject.class).info(e);
      return false;
    }
  }

  private static class PropertyNamePattern {
    @NotNull private final String myPattern;
    @Nullable private final Pattern myCompiledPattern;
    @Nullable private final String myPatternError;
    @NotNull private final Map<String, Boolean> myValuePatternCache;

    public PropertyNamePattern(@NotNull String pattern) {
      myPattern = StringUtil.unescapeBackSlashes(pattern);
      final Pair<Pattern, String> pair = compilePattern(pattern);
      myPatternError = pair.getSecond();
      myCompiledPattern = pair.getFirst();
      myValuePatternCache = ContainerUtil.createConcurrentWeakKeyWeakValueMap();
    }

    @Nullable
    public String getPatternError() {
      return myPatternError;
    }

    boolean checkByPattern(@NotNull final String name) {
      if (myPatternError != null) return true;
      if (Boolean.TRUE.equals(myValuePatternCache.get(name))) return true;
      assert myCompiledPattern != null;
      boolean matches = matchPattern(myCompiledPattern, name);
      myValuePatternCache.put(name, matches);
      return matches;
    }

    @NotNull
    public String getPattern() {
      return myPattern;
    }
  }

  private static class PatternProperties {
    @NotNull private final Map<String, JsonSchemaObject> mySchemasMap;
    @NotNull private final Map<String, Pattern> myCachedPatterns;
    @NotNull private final Map<String, String> myCachedPatternProperties;
    @NotNull private final Map<String, String> myInvalidPatterns;

    public PatternProperties(@NotNull final Map<String, JsonSchemaObject> schemasMap) {
      mySchemasMap = new HashMap<>();
      schemasMap.keySet().forEach(key -> mySchemasMap.put(StringUtil.unescapeBackSlashes(key), schemasMap.get(key)));
      myCachedPatterns = new HashMap<>();
      myCachedPatternProperties = ContainerUtil.createConcurrentWeakKeyWeakValueMap();
      myInvalidPatterns = new HashMap<>();
      mySchemasMap.keySet().forEach(key -> {
        final Pair<Pattern, String> pair = compilePattern(key);
        if (pair.getSecond() != null) {
          myInvalidPatterns.put(key, pair.getSecond());
        } else {
          assert pair.getFirst() != null;
          myCachedPatterns.put(key, pair.getFirst());
        }
      });
    }

    @Nullable
    public JsonSchemaObject getPatternPropertySchema(@NotNull final String name) {
      String value = myCachedPatternProperties.get(name);
      if (value != null) {
        assert mySchemasMap.containsKey(value);
        return mySchemasMap.get(value);
      }

      value = myCachedPatterns.keySet().stream()
        .filter(key -> matchPattern(myCachedPatterns.get(key), name))
        .findFirst()
        .orElse(null);
      if (value != null) {
        myCachedPatternProperties.put(name, value);
        assert mySchemasMap.containsKey(value);
        return mySchemasMap.get(value);
      }
      return null;
    }

    @NotNull
    public Map<String, String> getInvalidPatterns() {
      return myInvalidPatterns;
    }

    public JsonSchemaObject getSchemaForPattern(@NotNull String key) {
      return mySchemasMap.get(key);
    }
  }
}
