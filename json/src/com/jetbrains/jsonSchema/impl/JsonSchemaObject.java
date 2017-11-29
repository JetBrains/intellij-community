package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonObject;
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
  @NotNull private final JsonObject myJsonObject;
  private Map<String, JsonSchemaObject> myDefinitionsMap;
  private Map<String, JsonSchemaObject> myProperties;

  private PatternProperties myPatternProperties;
  private PropertyNamePattern myPattern;

  private String myId;
  private String mySchema;
  private String myDescription;

  private String myTitle;

  private JsonSchemaType myType;
  private Object myDefault;
  private String myRef;
  private String myFormat;
  private List<JsonSchemaType> myTypeVariants;
  private Number myMultipleOf;
  private Number myMaximum;
  private boolean myExclusiveMaximum;
  private Number myMinimum;
  private boolean myExclusiveMinimum;
  private Integer myMaxLength;
  private Integer myMinLength;

  private Boolean myAdditionalPropertiesAllowed;
  private JsonSchemaObject myAdditionalPropertiesSchema;

  private Boolean myAdditionalItemsAllowed;
  private JsonSchemaObject myAdditionalItemsSchema;

  private JsonSchemaObject myItemsSchema;
  private List<JsonSchemaObject> myItemsSchemaList;

  private Integer myMaxItems;
  private Integer myMinItems;

  private Boolean myUniqueItems;

  private Integer myMaxProperties;
  private Integer myMinProperties;
  private List<String> myRequired;

  private Map<String, List<String>> myPropertyDependencies;
  private Map<String, JsonSchemaObject> mySchemaDependencies;

  private List<Object> myEnum;

  private List<JsonSchemaObject> myAllOf;
  private List<JsonSchemaObject> myAnyOf;
  private List<JsonSchemaObject> myOneOf;
  private JsonSchemaObject myNot;
  private boolean myShouldValidateAgainstJSType;

  public JsonSchemaObject(@NotNull JsonObject object) {
    myJsonObject = object;
    myProperties = new HashMap<>();
  }

  // peer pointer is not merged!
  public void mergeValues(JsonSchemaObject other) {
    // we do not copy id, schema

    myProperties.putAll(other.myProperties);
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

    if (other.myType != null) myType = other.myType;
    if (other.myDefault != null) myDefault = other.myDefault;
    if (other.myRef != null) myRef = other.myRef;
    if (other.myFormat != null) myFormat = other.myFormat;
    myTypeVariants = copyList(myTypeVariants, other.myTypeVariants);
    if (other.myMultipleOf != null) myMultipleOf = other.myMultipleOf;
    if (other.myMaximum != null) myMaximum = other.myMaximum;
    if (other.myExclusiveMaximum) myExclusiveMaximum = other.myExclusiveMaximum;
    if (other.myMinimum != null) myMinimum = other.myMinimum;
    if (other.myExclusiveMinimum) myExclusiveMinimum = other.myExclusiveMinimum;
    if (other.myMaxLength != null) myMaxLength = other.myMaxLength;
    if (other.myMinLength != null) myMinLength = other.myMinLength;
    if (other.myPattern != null) myPattern = other.myPattern;
    if (other.myAdditionalPropertiesAllowed != null) myAdditionalPropertiesAllowed = other.myAdditionalPropertiesAllowed;
    if (other.myAdditionalPropertiesSchema != null) myAdditionalPropertiesSchema = other.myAdditionalPropertiesSchema;
    if (other.myAdditionalItemsAllowed != null) myAdditionalItemsAllowed = other.myAdditionalItemsAllowed;
    if (other.myAdditionalItemsSchema != null) myAdditionalItemsSchema = other.myAdditionalItemsSchema;
    if (other.myItemsSchema != null) myItemsSchema = other.myItemsSchema;
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
    myShouldValidateAgainstJSType |= other.myShouldValidateAgainstJSType;
  }

  public void shouldValidateAgainstJSType() {
    myShouldValidateAgainstJSType = true;
  }

  public boolean isShouldValidateAgainstJSType() {
    return myShouldValidateAgainstJSType;
  }

  private static <T> List<T> copyList(List<T> target, List<T> source) {
    if (source == null || source.isEmpty()) return target;
    if (target == null) target = new ArrayList<>();
    target.addAll(source);
    return target;
  }

  private static <K, V> Map<K, V> copyMap(Map<K, V> target, Map<K, V> source) {
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
  public JsonObject getJsonObject() {
    return myJsonObject;
  }

  public Map<String, JsonSchemaObject> getDefinitionsMap() {
    return myDefinitionsMap;
  }

  public void setDefinitionsMap(@NotNull Map<String, JsonSchemaObject> definitionsMap) {
    myDefinitionsMap = definitionsMap;
  }

  public Map<String, JsonSchemaObject> getProperties() {
    return myProperties;
  }

  public void setProperties(Map<String, JsonSchemaObject> properties) {
    myProperties = properties;
  }

  public void setPatternProperties(Map<String, JsonSchemaObject> patternProperties) {
    myPatternProperties = new PatternProperties(patternProperties);
  }

  @Nullable
  public JsonSchemaType getType() {
    return myType;
  }

  public void setType(JsonSchemaType type) {
    myType = type;
  }

  public Number getMultipleOf() {
    return myMultipleOf;
  }

  public void setMultipleOf(Number multipleOf) {
    myMultipleOf = multipleOf;
  }

  public Number getMaximum() {
    return myMaximum;
  }

  public void setMaximum(Number maximum) {
    myMaximum = maximum;
  }

  public boolean isExclusiveMaximum() {
    return myExclusiveMaximum;
  }

  public void setExclusiveMaximum(boolean exclusiveMaximum) {
    myExclusiveMaximum = exclusiveMaximum;
  }

  public Number getMinimum() {
    return myMinimum;
  }

  public void setMinimum(Number minimum) {
    myMinimum = minimum;
  }

  public boolean isExclusiveMinimum() {
    return myExclusiveMinimum;
  }

  public void setExclusiveMinimum(boolean exclusiveMinimum) {
    myExclusiveMinimum = exclusiveMinimum;
  }

  public Integer getMaxLength() {
    return myMaxLength;
  }

  public void setMaxLength(Integer maxLength) {
    myMaxLength = maxLength;
  }

  public Integer getMinLength() {
    return myMinLength;
  }

  public void setMinLength(Integer minLength) {
    myMinLength = minLength;
  }

  public String getPattern() {
    return myPattern == null ? null : myPattern.getPattern();
  }

  public void setPattern(String pattern) {
    myPattern = pattern == null ? null : new PropertyNamePattern(pattern);
  }

  public Boolean getAdditionalPropertiesAllowed() {
    return myAdditionalPropertiesAllowed == null || myAdditionalPropertiesAllowed;
  }

  public void setAdditionalPropertiesAllowed(Boolean additionalPropertiesAllowed) {
    myAdditionalPropertiesAllowed = additionalPropertiesAllowed;
  }

  public JsonSchemaObject getAdditionalPropertiesSchema() {
    return myAdditionalPropertiesSchema;
  }

  public void setAdditionalPropertiesSchema(JsonSchemaObject additionalPropertiesSchema) {
    myAdditionalPropertiesSchema = additionalPropertiesSchema;
  }

  public Boolean getAdditionalItemsAllowed() {
    return myAdditionalItemsAllowed == null || myAdditionalItemsAllowed;
  }

  public void setAdditionalItemsAllowed(Boolean additionalItemsAllowed) {
    myAdditionalItemsAllowed = additionalItemsAllowed;
  }

  public JsonSchemaObject getAdditionalItemsSchema() {
    return myAdditionalItemsSchema;
  }

  public void setAdditionalItemsSchema(JsonSchemaObject additionalItemsSchema) {
    myAdditionalItemsSchema = additionalItemsSchema;
  }

  public JsonSchemaObject getItemsSchema() {
    return myItemsSchema;
  }

  public void setItemsSchema(JsonSchemaObject itemsSchema) {
    myItemsSchema = itemsSchema;
  }

  public List<JsonSchemaObject> getItemsSchemaList() {
    return myItemsSchemaList;
  }

  public void setItemsSchemaList(List<JsonSchemaObject> itemsSchemaList) {
    myItemsSchemaList = itemsSchemaList;
  }

  public Integer getMaxItems() {
    return myMaxItems;
  }

  public void setMaxItems(Integer maxItems) {
    myMaxItems = maxItems;
  }

  public Integer getMinItems() {
    return myMinItems;
  }

  public void setMinItems(Integer minItems) {
    myMinItems = minItems;
  }

  public boolean isUniqueItems() {
    return Boolean.TRUE.equals(myUniqueItems);
  }

  public void setUniqueItems(boolean uniqueItems) {
    myUniqueItems = uniqueItems;
  }

  public Integer getMaxProperties() {
    return myMaxProperties;
  }

  public void setMaxProperties(Integer maxProperties) {
    myMaxProperties = maxProperties;
  }

  public Integer getMinProperties() {
    return myMinProperties;
  }

  public void setMinProperties(Integer minProperties) {
    myMinProperties = minProperties;
  }

  public List<String> getRequired() {
    return myRequired;
  }

  public void setRequired(List<String> required) {
    myRequired = required;
  }

  public Map<String, List<String>> getPropertyDependencies() {
    return myPropertyDependencies;
  }

  public void setPropertyDependencies(Map<String, List<String>> propertyDependencies) {
    myPropertyDependencies = propertyDependencies;
  }

  public Map<String, JsonSchemaObject> getSchemaDependencies() {
    return mySchemaDependencies;
  }

  public void setSchemaDependencies(Map<String, JsonSchemaObject> schemaDependencies) {
    mySchemaDependencies = schemaDependencies;
  }

  public List<Object> getEnum() {
    return myEnum;
  }

  public void setEnum(List<Object> anEnum) {
    myEnum = anEnum;
  }

  public List<JsonSchemaObject> getAllOf() {
    return myAllOf;
  }

  public void setAllOf(List<JsonSchemaObject> allOf) {
    myAllOf = allOf;
  }

  public List<JsonSchemaObject> getAnyOf() {
    return myAnyOf;
  }

  public void setAnyOf(List<JsonSchemaObject> anyOf) {
    myAnyOf = anyOf;
  }

  public List<JsonSchemaObject> getOneOf() {
    return myOneOf;
  }

  public void setOneOf(List<JsonSchemaObject> oneOf) {
    myOneOf = oneOf;
  }

  public JsonSchemaObject getNot() {
    return myNot;
  }

  public void setNot(JsonSchemaObject not) {
    myNot = not;
  }

  public List<JsonSchemaType> getTypeVariants() {
    return myTypeVariants;
  }

  public void setTypeVariants(List<JsonSchemaType> typeVariants) {
    myTypeVariants = typeVariants;
  }

  public String getRef() {
    return myRef;
  }

  public void setRef(String ref) {
    myRef = ref;
  }

  public Object getDefault() {
    if (JsonSchemaType._integer.equals(myType)) return myDefault instanceof Number ? ((Number)myDefault).intValue() : myDefault;
    return myDefault;
  }

  public void setDefault(Object aDefault) {
    myDefault = aDefault;
  }

  public String getFormat() {
    return myFormat;
  }

  public void setFormat(String format) {
    myFormat = format;
  }

  public String getId() {
    return myId;
  }

  public void setId(String id) {
    myId = id;
  }

  public String getSchema() {
    return mySchema;
  }

  public void setSchema(String schema) {
    mySchema = schema;
  }

  public String getDescription() {
    return myDescription;
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  public String getTitle() {
    return myTitle;
  }

  public void setTitle(String title) {
    myTitle = title;
  }

  @Nullable
  public JsonSchemaObject getMatchingPatternPropertySchema(@NotNull String name) {
    if (myPatternProperties == null) return null;
    return myPatternProperties.getPatternPropertySchema(name);
  }

  public boolean checkByPattern(@NotNull String value) {
    return myPattern != null && myPattern.checkByPattern(value);
  }

  public String getPatternError() {
    return myPattern == null ? null : myPattern.getPatternError();
  }

  public Map<JsonObject, String> getInvalidPatternProperties() {
    if (myPatternProperties != null) {
      final Map<String, String> patterns = myPatternProperties.getInvalidPatterns();
      if (patterns == null) return null;

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
        current = current.getDefinitionsMap().get(parts.get(++i));
        continue;
      }
      if (PROPERTIES.equals(part)) {
        if (i == (parts.size() - 1)) return null;
        //noinspection AssignmentToForLoopParameter
        current = current.getProperties().get(parts.get(++i));
        continue;
      }

      current = current.getDefinitionsMap().get(part);
    }
    return current;
  }

  @Nullable
  public String getDocumentation(final boolean preferShort) {
    if (preferShort) return StringUtil.isEmptyOrSpaces(myTitle) ? myDescription : myTitle;
    return StringUtil.isEmptyOrSpaces(myDescription) ? myTitle : myDescription;
  }

  @Override
  public boolean equals(Object o) {
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
    try {
      return pattern.matcher(StringUtil.newBombedCharSequence(s, 300)).matches();
    } catch (ProcessCanceledException e) {
      // something wrong with the pattern, infinite cycle?
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
    private final Map<String, JsonSchemaObject> mySchemasMap;
    private final Map<String, Pattern> myCachedPatterns;
    private final Map<String, String> myCachedPatternProperties;
    private final Map<String, String> myInvalidPatterns;

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

    public Map<String, String> getInvalidPatterns() {
      return myInvalidPatterns;
    }

    public JsonSchemaObject getSchemaForPattern(@NotNull String key) {
      return mySchemasMap.get(key);
    }
  }
}
