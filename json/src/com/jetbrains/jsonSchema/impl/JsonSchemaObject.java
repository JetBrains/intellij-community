package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonObject;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.Consumer;
import com.intellij.util.containers.SLRUMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @author Irina.Chernushina on 8/28/2015.
 */
public class JsonSchemaObject {
  private SmartPsiElementPointer<JsonObject> myPeerPointer;
  private String myDefinitionAddress;
  private Map<String, JsonSchemaObject> myDefinitions;
  private SmartPsiElementPointer<JsonObject> myDefinitionsPointer;
  private Map<String, JsonSchemaObject> myProperties;
  private Map<String, JsonSchemaObject> myPatternProperties;
  private final PatternCalculator myPatternCalculator = new PatternCalculator();

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

  public JsonSchemaObject(@NotNull JsonObject object) {
    myProperties = new HashMap<>();
    myPeerPointer = SmartPointerManager.getInstance(object.getProject()).createSmartPsiElementPointer(object);
  }

  // only for definitions
  public JsonSchemaObject(@Nullable SmartPsiElementPointer<JsonObject> peerPointer) {
    myProperties = new HashMap<>();
    myPeerPointer = peerPointer;
  }

  // full copy. allows to first apply properties for ref, then from definition itself, "in place"
  public void copyValues(JsonSchemaObject other) {
    myId = other.myId;
    mySchema = other.mySchema;
    myDescription = other.myDescription;
    myTitle = other.myTitle;

    myProperties = other.myProperties;
    myDefinitions = other.myDefinitions;
    myPatternProperties = other.myPatternProperties;
    myPatternCalculator.clear();

    myType = other.myType;
    myDefault = other.myDefault;
    myRef = other.myRef;
    myFormat = other.myFormat;
    myTypeVariants = other.myTypeVariants;
    myMultipleOf = other.myMultipleOf;
    myMaximum = other.myMaximum;
    myExclusiveMaximum = other.myExclusiveMaximum;
    myMinimum = other.myMinimum;
    myExclusiveMinimum = other.myExclusiveMinimum;
    myMaxLength = other.myMaxLength;
    myMinLength = other.myMinLength;
    myPattern = other.myPattern;
    myAdditionalPropertiesAllowed = other.myAdditionalPropertiesAllowed;
    myAdditionalPropertiesSchema = other.myAdditionalPropertiesSchema;
    myAdditionalItemsAllowed = other.myAdditionalItemsAllowed;
    myAdditionalItemsSchema = other.myAdditionalItemsSchema;
    myItemsSchema = other.myItemsSchema;
    myItemsSchemaList = other.myItemsSchemaList;
    myMaxItems = other.myMaxItems;
    myMinItems = other.myMinItems;
    myUniqueItems = other.myUniqueItems;
    myMaxProperties = other.myMaxProperties;
    myMinProperties = other.myMinProperties;
    myRequired = other.myRequired;
    myPropertyDependencies = other.myPropertyDependencies;
    mySchemaDependencies = other.mySchemaDependencies;
    myEnum = other.myEnum;
    myAllOf = other.myAllOf;
    myAnyOf = other.myAnyOf;
    myOneOf = other.myOneOf;
    myNot = other.myNot;
    myDefinitionAddress = other.myDefinitionAddress;
    myPeerPointer = other.myPeerPointer;
  }

  // peer pointer is not merged!
  public void mergeValues(JsonSchemaObject other) {
    // we do not copy id, schema, title and description

    myProperties.putAll(other.myProperties);
    myDefinitions = copyMap(myDefinitions, other.myDefinitions);
    myPatternProperties = copyMap(myPatternProperties, other.myPatternProperties);
    myPatternCalculator.clear();
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

  public SmartPsiElementPointer<JsonObject> getPeerPointer() {
    return myPeerPointer;
  }

  public void setPeerPointer(SmartPsiElementPointer<JsonObject> peerPointer) {
    myPeerPointer = peerPointer;
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

  public Map<String, JsonSchemaObject> getPatternProperties() {
    return myPatternProperties;
  }

  public void setPatternProperties(Map<String, JsonSchemaObject> patternProperties) {
    myPatternProperties = patternProperties;
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
    myPattern = pattern;
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

  public String getDefinitionAddress() {
    return myDefinitionAddress;
  }

  public void setDefinitionAddress(String definitionAddress) {
    myDefinitionAddress = definitionAddress;
  }

  @Nullable
  public JsonSchemaObject getMatchingPatternPropertySchema(@NotNull String name) {
    return myPatternCalculator.getMatchingPatternPropertySchema(myPatternProperties, name);
  }

  @NotNull
  private static String adaptSchemaPattern(String pattern) {
    pattern = pattern.startsWith("^") || pattern.startsWith("*") || pattern.startsWith(".") ? pattern : (".*" + pattern);
    pattern = pattern.endsWith("+") || pattern.endsWith("*") ? pattern : (pattern + ".*");
    return pattern;
  }

  public static void iterateAllInnerSchemas(@NotNull final JsonSchemaObject object, @NotNull final SchemaConsumer schemaConsumer) {
    int control = 100000;
    final ArrayDeque<Pair<JsonSchemaObject, Map<String, String>>> queue = new ArrayDeque<>();
    queue.add(Pair.create(object, new HashMap<>()));
    while(!queue.isEmpty()) {
      if (--control == 0) {
        throw new RuntimeException("cyclic json schema search");
      }
      final Pair<JsonSchemaObject, Map<String, String>> pair = queue.removeFirst();
      final JsonSchemaObject current = pair.getFirst();
      final Map<String, String> context = pair.getSecond();

      final Ref<JsonSchemaObject> previous = new Ref<>();
      final Ref<Map<String, String>> childContextRef = new Ref<>(context);
      schemaConsumer.process(current, item -> previous.set(item), context, childContext -> childContextRef.set(childContext));

      if (!previous.isNull()) {
        queue.addFirst(Pair.create(current, context));
        queue.addFirst(Pair.create(previous.get(), context));
        continue;
      }

      final List<JsonSchemaObject> list = new ArrayList<>();
      if (current.getDefinitions() != null) list.addAll(current.getDefinitions().values());
      if (current.getProperties() != null) list.addAll(current.getProperties().values());
      if (current.getPatternProperties() != null) list.addAll(current.getPatternProperties().values());
      if (current.getAdditionalPropertiesSchema() != null) list.add(current.getAdditionalPropertiesSchema());
      if (current.getAdditionalItemsSchema() != null) list.add(current.getAdditionalItemsSchema());
      if (current.getItemsSchema() != null) list.add(current.getItemsSchema());
      if (current.getItemsSchemaList() != null) list.addAll(current.getItemsSchemaList());
      if (current.getSchemaDependencies() != null) list.addAll(current.getSchemaDependencies().values());

      if (current.getAllOf() != null) list.addAll(current.getAllOf());
      if (current.getAnyOf() != null) list.addAll(current.getAnyOf());
      if (current.getOneOf() != null) list.addAll(current.getOneOf());
      if (current.getNot() != null) list.add(current.getNot());

      for (JsonSchemaObject schemaObject : list) {
        queue.addLast(Pair.create(schemaObject, childContextRef.get()));
      }
    }
  }

  public interface SchemaConsumer {
    void process(@NotNull JsonSchemaObject object, Consumer<JsonSchemaObject> queueInserter,
                       @NotNull Map<String, String> context,
                       @NotNull Consumer<Map<String, String>> contextChanger);
  }

  private static class PatternCalculator {
    private final Object myLock = new Object();
    private Map<String, Pattern> myCachedPatterns;
    private SLRUMap<String, String> myCachedPatternProperties;

    @Nullable
    public JsonSchemaObject getMatchingPatternPropertySchema(@Nullable final Map<String, JsonSchemaObject> patternProperties,
                                                             @NotNull final String name) {
      if (patternProperties == null || patternProperties.isEmpty()) return null;
      final Map<String, Pattern> patterns;
      synchronized (myLock) {
        if (myCachedPatterns == null) {
          initPatternCache(patternProperties);
        } else {
          assert myCachedPatternProperties != null;
          final String s = myCachedPatternProperties.get(name);
          if (s != null) return patternProperties.get(s);
        }
        patterns = new HashMap<>(myCachedPatterns);
      }

      return matchPatternsToString(name, patternProperties, patterns);
    }

    public void clear() {
      synchronized (myLock){
        myCachedPatterns = null;
        myCachedPatternProperties = null;
      }
    }

    private JsonSchemaObject matchPatternsToString(@NotNull final String name,
                                                   @NotNull final Map<String, JsonSchemaObject> patternProperties,
                                                   @NotNull Map<String, Pattern> patterns) {
      final List<String> strings = new ArrayList<>(patternProperties.keySet());
      Collections.sort(strings);

      for (final String pattern : strings) {
        final Pattern compiledPattern = patterns.get(pattern);
        assert compiledPattern != null;
        try {
          final boolean matches = compiledPattern.matcher(StringUtil.newBombedCharSequence(name, 300)).matches();
          if (matches) {
            synchronized (myLock) {
              if (myCachedPatterns.containsKey(pattern)) myCachedPatternProperties.put(name, pattern);
            }
            return patternProperties.get(pattern);
          }
        } catch (ProcessCanceledException e) {
          //ignored
        }
      }
      synchronized (myLock) {
        if (myCachedPatterns.equals(patterns)) myCachedPatternProperties.put(name, "");
      }
      return null;
    }

    private void initPatternCache(@NotNull final Map<String, JsonSchemaObject> patternProperties) {
      myCachedPatterns = new HashMap<>(patternProperties.size(), 1.0f);
      myCachedPatternProperties = new SLRUMap<>(100, 100);
      for (String pattern : patternProperties.keySet()) {
        myCachedPatterns.put(pattern, Pattern.compile(adaptSchemaPattern(pattern)));
      }
    }
  }
}
