package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonObject;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.containers.SLRUMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * @author Irina.Chernushina on 8/28/2015.
 */
public class JsonSchemaObject {
  @NonNls public static final String DEFINITIONS = "definitions";
  @NonNls public static final String PROPERTIES = "properties";
  @NotNull
  private SmartPsiElementPointer<JsonObject> myPeerPointer;
  private Map<String, JsonSchemaObject> myDefinitions;
  private SmartPsiElementPointer<JsonObject> myDefinitionsPointer;
  private Map<String, JsonSchemaObject> myProperties;
  private Map<String, JsonSchemaObject> myPatternProperties;
  private final PatternCalculator myPatternCalculator = new PatternCalculator();
  private final PatternCalculator myValuesPatternCalculator = new PatternCalculator();

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
  private String myPattern;

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
    myProperties = new HashMap<>();
    myPeerPointer = SmartPointerManager.getInstance(object.getProject()).createSmartPsiElementPointer(object);
  }

  public JsonSchemaObject(@NotNull SmartPsiElementPointer<JsonObject> peerPointer) {
    myProperties = new HashMap<>();
    myPeerPointer = peerPointer;
  }

  // peer pointer is not merged!
  public void mergeValues(JsonSchemaObject other) {
    // we do not copy id, schema, title and description

    myProperties.putAll(other.myProperties);
    myDefinitions = copyMap(myDefinitions, other.myDefinitions);
    myPatternProperties = copyMap(myPatternProperties, other.myPatternProperties);
    myPatternCalculator.clear();
    myValuesPatternCalculator.clear();
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

  @Nullable
  public VirtualFile getSchemaFile() {
    return myPeerPointer.getVirtualFile();
  }

  @NotNull
  public SmartPsiElementPointer<JsonObject> getPeerPointer() {
    return myPeerPointer;
  }

  public SmartPsiElementPointer<JsonObject> getDefinitionsPointer() {
    return myDefinitionsPointer;
  }

  public void setDefinitionsPointer(SmartPsiElementPointer<JsonObject> definitionsPointer) {
    myDefinitionsPointer = definitionsPointer;
  }

  public Map<String, JsonSchemaObject> getDefinitions() {
    return myDefinitions;
  }

  public void setDefinitions(Map<String, JsonSchemaObject> definitions) {
    myDefinitions = definitions;
  }

  public Map<String, JsonSchemaObject> getProperties() {
    return myProperties;
  }

  public void setProperties(Map<String, JsonSchemaObject> properties) {
    myProperties = properties;
  }

  public void setPatternProperties(@NotNull final Map<String, JsonSchemaObject> patternProperties) {
    myPatternProperties = new HashMap<>();
    patternProperties.keySet().forEach(key -> myPatternProperties.put(StringUtil.unescapeBackSlashes(key), patternProperties.get(key)));
    myPatternCalculator.clear();
  }

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
    return myPattern;
  }

  public void setPattern(String pattern) {
    myPattern = StringUtil.unescapeBackSlashes(pattern);
    myValuesPatternCalculator.clear();
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

  public boolean hasSpecifiedType() {
    return myType != null || (myTypeVariants != null && !myTypeVariants.isEmpty());
  }

  @Nullable
  public JsonSchemaObject getMatchingPatternPropertySchema(@NotNull String name) {
    if (myPatternProperties == null) return null;
    final String pattern = myPatternCalculator.selectMatchingPattern(myPatternProperties.keySet(), name);
    return pattern == null ? null : myPatternProperties.get(pattern);
  }

  public boolean checkByPattern(@NotNull String value) {
    if (getPattern() == null) return true;
    return getPattern().equals(myValuesPatternCalculator.selectMatchingPattern(Collections.singletonList(getPattern()), value));
  }

  public String getPatternError() {
    if (getPattern() != null) {
      myValuesPatternCalculator.ensureInit(Collections.singletonList(getPattern()));
      final Map<String, String> patterns = myValuesPatternCalculator.getInvalidPatterns();
      if (patterns != null) {
        final String error = patterns.get(getPattern());
        assert error != null;
        return error;
      }
    }
    return null;
  }

  public Map<SmartPsiElementPointer<JsonObject>, String> getInvalidPatternProperties() {
    if (myPatternProperties != null) {
      myPatternCalculator.ensureInit(myPatternProperties.keySet());
      final Map<String, String> patterns = myPatternCalculator.getInvalidPatterns();
      if (patterns == null) return null;

      return patterns.entrySet().stream().map(entry -> {
        final JsonSchemaObject object = myPatternProperties.get(entry.getKey());
        assert object != null;
        final SmartPsiElementPointer<JsonObject> pointer = object.getPeerPointer();
        return Pair.create(pointer, entry.getValue());
      }).filter(o -> o != null).collect(Collectors.toMap(o -> o.getFirst(), o -> o.getSecond()));
    }
    return null;
  }

  @Nullable
  public JsonSchemaObject findRelativeDefinition(@NotNull String ref) {
    if ("#".equals(ref) || StringUtil.isEmpty(ref)) {
      return this;
    }
    if (!ref.startsWith("#/")) {
      throw new RuntimeException("Non-relative or erroneous reference: " + ref);
    }
    ref = ref.substring(2);
    final List<String> parts = StringUtil.split(ref, "/");
    JsonSchemaObject current = this;
    for (int i = 0; i < parts.size(); i++) {
      if (current == null) return null;
      final String part = parts.get(i);
      if (DEFINITIONS.equals(part)) {
        if (i == (parts.size() - 1)) throw new RuntimeException("Incorrect definition reference: " + ref);
        //noinspection AssignmentToForLoopParameter
        current = current.getDefinitions().get(parts.get(++i));
        continue;
      }
      if (PROPERTIES.equals(part)) {
        if (i == (parts.size() - 1)) throw new RuntimeException("Incorrect properties reference: " + ref);
        //noinspection AssignmentToForLoopParameter
        current = current.getProperties().get(parts.get(++i));
        continue;
      }

      current = current.getDefinitions().get(part);
    }
    return current;
  }

  private static class PatternCalculator {
    private final Object myLock = new Object();
    private Map<String, Pattern> myCachedPatterns;
    private SLRUMap<String, String> myCachedPatternProperties;
    private Map<String, String> myInvalidPatterns;

    @Nullable
    public String selectMatchingPattern(@Nullable final Collection<String> patterns, @NotNull final String name) {
      if (patterns == null || patterns.isEmpty()) return null;
      final Map<String, Pattern> cachedPatterns;
      final Map<String, String> invalidPatterns;
      synchronized (myLock) {
        initPatternCache(patterns);
        final String s = myCachedPatternProperties.get(name);
        if (s != null) return s;
        cachedPatterns = new HashMap<>(myCachedPatterns);
        invalidPatterns = myInvalidPatterns == null ? Collections.emptyMap() : new HashMap<>(myInvalidPatterns);
      }

      return matchPatternsToString(name, patterns, cachedPatterns, invalidPatterns);
    }

    public void ensureInit(@Nullable final Collection<String> patterns) {
      if (patterns == null || patterns.isEmpty()) return;
      synchronized (myLock) {
        initPatternCache(patterns);
      }
    }

    @Nullable
    public Map<String, String> getInvalidPatterns() {
      return myInvalidPatterns;
    }

    public void clear() {
      synchronized (myLock){
        myCachedPatterns = null;
        myCachedPatternProperties = null;
      }
    }

    private String matchPatternsToString(@NotNull final String name,
                                         @NotNull final Collection<String> patterns,
                                         @NotNull Map<String, Pattern> cachedPatterns,
                                         @Nullable Map<String, String> invalidPatterns) {
      final List<String> strings = new ArrayList<>(patterns);
      Collections.sort(strings);

      for (final String pattern : strings) {
        if (invalidPatterns != null && invalidPatterns.containsKey(pattern)) continue;
        final Pattern compiledPattern = cachedPatterns.get(pattern);
        assert compiledPattern != null;
        try {
          final boolean matches = compiledPattern.matcher(StringUtil.newBombedCharSequence(name, 300)).matches();
          if (matches) {
            synchronized (myLock) {
              if (myCachedPatterns.containsKey(pattern)) myCachedPatternProperties.put(name, pattern);
            }
            return pattern;
          }
        } catch (ProcessCanceledException e) {
          //ignored
        }
      }
      synchronized (myLock) {
        if (myCachedPatterns.equals(cachedPatterns)) myCachedPatternProperties.put(name, "");
      }
      return null;
    }

    private void initPatternCache(@NotNull final Collection<String> patterns) {
      if (myCachedPatterns == null) {
        myCachedPatterns = new HashMap<>(patterns.size());
        myCachedPatternProperties = new SLRUMap<>(100, 100);
      }
      for (String pattern : patterns) {
        if (!myCachedPatterns.containsKey(pattern)) {
          try {
            final Pattern compiled = Pattern.compile(adaptSchemaPattern(pattern));
            myCachedPatterns.put(pattern, compiled);
          } catch (PatternSyntaxException e) {
            if (myInvalidPatterns == null) myInvalidPatterns = new HashMap<>();
            myInvalidPatterns.put(pattern, e.getMessage());
          }
        }
      }
    }

    @NotNull
    private static String adaptSchemaPattern(String pattern) {
      pattern = pattern.startsWith("^") || pattern.startsWith("*") || pattern.startsWith(".") ? pattern : (".*" + pattern);
      pattern = pattern.endsWith("+") || pattern.endsWith("*") || pattern.endsWith("$") ? pattern : (pattern + ".*");
      pattern = pattern.replace("\\\\", "\\");
      return pattern;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    JsonSchemaObject object = (JsonSchemaObject)o;

    if (!myPeerPointer.equals(object.myPeerPointer)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myPeerPointer.hashCode();
  }
}
