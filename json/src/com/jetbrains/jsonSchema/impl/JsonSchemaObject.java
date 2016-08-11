package com.jetbrains.jsonSchema.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Irina.Chernushina on 8/28/2015.
 */
public class JsonSchemaObject {
  private String myDefinitionAddress;
  private Map<String, JsonSchemaObject> myDefinitions;
  private Map<String, JsonSchemaObject> myProperties;
  private Map<String, JsonSchemaObject> myPatternProperties;

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

  private Boolean myAdditionalPropertiesAllowed = true;
  private JsonSchemaObject myAdditionalPropertiesSchema;

  private Boolean myAdditionalItemsAllowed = true;
  private JsonSchemaObject myAdditionalItemsSchema;

  private JsonSchemaObject myItemsSchema;
  private List<JsonSchemaObject> myItemsSchemaList;

  private Integer myMaxItems;
  private Integer myMinItems;

  private boolean myUniqueItems;

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

  public JsonSchemaObject() {
    myProperties = new HashMap<>();
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
  }

  public void mergeValues(JsonSchemaObject other) {
    // we do not copy id, schema, title and description

    myProperties.putAll(other.myProperties);
    myDefinitions = copyMap(myDefinitions, other.myDefinitions);
    myPatternProperties = copyMap(myPatternProperties, other.myPatternProperties);

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
    if (other.myUniqueItems) myUniqueItems = other.myUniqueItems;
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
    return myAdditionalPropertiesAllowed;
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
    return myAdditionalItemsAllowed;
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
    return myUniqueItems;
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
}
