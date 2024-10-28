// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.JsonDependencyModificationTracker;
import com.jetbrains.jsonSchema.JsonPointerUtil;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.jetbrains.jsonSchema.impl.validations.JsonSchemaValidationsCollectorKt.getSchema7AndEarlierValidations;

@Deprecated
public class JsonSchemaObjectImpl extends JsonSchemaObject {
  public static final Logger LOG = Logger.getInstance(JsonSchemaObjectImpl.class);

  public static final @NonNls String DEFINITIONS = "definitions";
  public static final @NonNls String DEFINITIONS_v9 = "$defs";
  public static final @NonNls String PROPERTIES = "properties";
  public static final @NonNls String ITEMS = "items";
  public static final @NonNls String ADDITIONAL_ITEMS = "additionalItems";
  public final @Nullable String myFileUrl;
  public @Nullable JsonSchemaObjectImpl myBackRef;
  public final @NotNull String myPointer;
  public final @Nullable VirtualFile myRawFile;
  public @Nullable Map<String, JsonSchemaObjectImpl> myDefinitionsMap;
  public @NotNull Map<String, JsonSchemaObjectImpl> myProperties;

  public @Nullable PatternProperties myPatternProperties;
  public @Nullable PropertyNamePattern myPattern;

  public @Nullable String myId;
  public @Nullable String mySchema;

  public @Nullable String myTitle;
  public @Nullable String myDescription;
  public @Nullable String myHtmlDescription;
  public @Nullable String myLanguageInjection;
  public @Nullable String myLanguageInjectionPrefix;
  public @Nullable String myLanguageInjectionPostfix;

  public @Nullable List<JsonSchemaMetadataEntry> myMetadataEntries;

  public @Nullable JsonSchemaType myType;
  public @Nullable Object myDefault;
  public @Nullable Map<String, Object> myExample;
  public @Nullable String myRef;
  public boolean myRefIsRecursive;
  public boolean myIsRecursiveAnchor;
  public @Nullable String myFormat;
  public @Nullable Set<JsonSchemaType> myTypeVariants;
  public @Nullable Number myMultipleOf;
  public @Nullable Number myMaximum;
  public boolean myExclusiveMaximum;
  public @Nullable Number myExclusiveMaximumNumber;
  public @Nullable Number myMinimum;
  public boolean myExclusiveMinimum;
  public @Nullable Number myExclusiveMinimumNumber;
  public @Nullable Integer myMaxLength;
  public @Nullable Integer myMinLength;

  public @Nullable Boolean myAdditionalPropertiesAllowed;
  public @Nullable Set<String> myAdditionalPropertiesNotAllowedFor;
  public @Nullable JsonSchemaObjectImpl myAdditionalPropertiesSchema;
  public @Nullable JsonSchemaObjectImpl myPropertyNamesSchema;

  public @Nullable Boolean myAdditionalItemsAllowed;
  public @Nullable JsonSchemaObjectImpl myAdditionalItemsSchema;

  public @Nullable JsonSchemaObjectImpl myItemsSchema;
  public @Nullable JsonSchemaObjectImpl myContainsSchema;
  public @Nullable List<JsonSchemaObjectImpl> myItemsSchemaList;

  public @Nullable Integer myMaxItems;
  public @Nullable Integer myMinItems;

  public @Nullable Boolean myUniqueItems;

  public @Nullable Integer myMaxProperties;
  public @Nullable Integer myMinProperties;
  public @Nullable Set<String> myRequired;

  public @Nullable Map<String, List<String>> myPropertyDependencies;
  public @Nullable Map<String, JsonSchemaObjectImpl> mySchemaDependencies;

  public @Nullable List<Object> myEnum;

  public @Nullable List<JsonSchemaObjectImpl> myAllOf;
  public @Nullable List<JsonSchemaObjectImpl> myAnyOf;
  public @Nullable List<JsonSchemaObjectImpl> myOneOf;
  public @Nullable JsonSchemaObjectImpl myNot;
  public @Nullable List<IfThenElse> myIfThenElse;
  public @Nullable JsonSchemaObjectImpl myIf;
  public @Nullable JsonSchemaObjectImpl myThen;
  public @Nullable JsonSchemaObjectImpl myElse;
  public boolean myShouldValidateAgainstJSType;

  public @Nullable String myDeprecationMessage;
  public @Nullable Map<String, String> myIdsMap;

  public @Nullable Map<String, Map<String, String>> myEnumMetadata;

  public boolean myForceCaseInsensitive = false;

  public final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();

  @Override
  public @Nullable String readChildNodeValue(@NotNull String childNodeName) {
    return null;
  }

  @Override
  public boolean hasChildNode(@NotNull String childNodeName) {
    return false;
  }

  @Override
  public @Nullable Boolean getConstantSchema() {
    return null;
  }

  @Override
  public boolean hasChildFieldsExcept(@NotNull List<@NotNull String> namesToSkip) {
    return false;
  }

  @Override
  public @NotNull Iterable<JsonSchemaValidation> getValidations(@Nullable JsonSchemaType type, @NotNull JsonValueAdapter value) {
    return new Iterable<>() {
      @NotNull
      @Override
      public Iterator<JsonSchemaValidation> iterator() {
        return getSchema7AndEarlierValidations(JsonSchemaObjectImpl.this, type, value).iterator();
      }
    };
  }

  @Override
  public @NotNull JsonSchemaObject getRootSchemaObject() {
    throw new UnsupportedOperationException("Do not use the method against old json schema implementation!");
  }

  @Override
  public boolean isValidByExclusion() {
    return myIsValidByExclusion;
  }

  public void setValidByExclusion(boolean validByExclusion) {
    myIsValidByExclusion = validByExclusion;
  }

  @Override
  public boolean isForceCaseInsensitive() {
    return myForceCaseInsensitive;
  }

  public void setForceCaseInsensitive(boolean forceCaseInsensitive) {
    myForceCaseInsensitive = forceCaseInsensitive;
  }

  public boolean myIsValidByExclusion = true;

  public JsonSchemaObjectImpl(@Nullable VirtualFile file, @NotNull String pointer) {
    myFileUrl = file == null ? null : file.getUrl();
    myRawFile = myFileUrl != null && JsonFileResolver.isTempOrMockUrl(myFileUrl) ? file : null;
    myPointer = pointer;
    myProperties = new HashMap<>();
  }

  public JsonSchemaObjectImpl(@Nullable VirtualFile rawFile, @Nullable String fileUrl, @NotNull String pointer) {
    myFileUrl = fileUrl;
    myRawFile = rawFile;
    myPointer = pointer;
    myProperties = new HashMap<>();
  }

  public JsonSchemaObjectImpl(@NotNull String pointer) {
    this(null, pointer);
  }

  public void completeInitialization(JsonValueAdapter jsonObject) {
    if (myIf != null) {
      myIfThenElse = new ArrayList<>();
      myIfThenElse.add(new IfThenElse(myIf, myThen, myElse));
    }

    myIdsMap = JsonCachedValues.getOrComputeIdsMap(jsonObject.getDelegate().getContainingFile());
  }

  @Override
  public @NotNull String getPointer() {
    return myPointer;
  }

  @Override
  public @Nullable String getFileUrl() {
    return myFileUrl;
  }

  /**
   * NOTE: Raw files are stored only in very specific cases such as mock files
   * This API should be used only as a fallback to trying to resolve file via its url returned by getFileUrl()
   */
  @Override
  public @Nullable VirtualFile getRawFile() {
    return myRawFile;
  }

  @Override
  public @Nullable List<JsonSchemaMetadataEntry> getMetadata() {
    return myMetadataEntries;
  }

  public void setMetadata(@Nullable List<JsonSchemaMetadataEntry> entries) {
    myMetadataEntries = entries;
  }

  public void setLanguageInjection(@Nullable String injection) {
    myLanguageInjection = injection;
  }

  public void setLanguageInjectionPrefix(@Nullable String prefix) {
    myLanguageInjectionPrefix = prefix;
  }

  public void setLanguageInjectionPostfix(@Nullable String postfix) {
    myLanguageInjectionPostfix = postfix;
  }

  @Override
  public @Nullable String getLanguageInjection() {
    return myLanguageInjection;
  }

  @Override
  public @Nullable String getLanguageInjectionPrefix() {
    return myLanguageInjectionPrefix;
  }

  @Override
  public @Nullable String getLanguageInjectionPostfix() {
    return myLanguageInjectionPostfix;
  }

  public static @Nullable JsonSchemaType getSubtypeOfBoth(@NotNull JsonSchemaType selfType,
                                                          @NotNull JsonSchemaType otherType) {
    if (otherType == JsonSchemaType._any) return selfType;
    if (selfType == JsonSchemaType._any) return otherType;
    return switch (selfType) {
      case _string -> otherType == JsonSchemaType._string || otherType == JsonSchemaType._string_number ? JsonSchemaType._string : null;
      case _number -> {
        if (otherType == JsonSchemaType._integer) yield JsonSchemaType._integer;
        yield otherType == JsonSchemaType._number || otherType == JsonSchemaType._string_number ? JsonSchemaType._number : null;
      }
      case _integer -> otherType == JsonSchemaType._number
                       || otherType == JsonSchemaType._string_number
                       || otherType == JsonSchemaType._integer ? JsonSchemaType._integer : null;
      case _object -> otherType == JsonSchemaType._object ? JsonSchemaType._object : null;
      case _array -> otherType == JsonSchemaType._array ? JsonSchemaType._array : null;
      case _boolean -> otherType == JsonSchemaType._boolean ? JsonSchemaType._boolean : null;
      case _null -> otherType == JsonSchemaType._null ? JsonSchemaType._null : null;
      case _string_number -> otherType == JsonSchemaType._integer
                             || otherType == JsonSchemaType._number
                             || otherType == JsonSchemaType._string
                             || otherType == JsonSchemaType._string_number ? otherType : null;
      default -> otherType;
    };
  }

  public @Nullable JsonSchemaType mergeTypes(@Nullable JsonSchemaType selfType,
                                             @Nullable JsonSchemaType otherType,
                                             @Nullable Set<JsonSchemaType> otherTypeVariants) {
    if (selfType == null) return otherType;
    if (otherType == null) {
      if (otherTypeVariants != null && !otherTypeVariants.isEmpty()) {
        Set<JsonSchemaType> filteredVariants = EnumSet.noneOf(JsonSchemaType.class);
        for (JsonSchemaType variant : otherTypeVariants) {
          JsonSchemaType subtype = getSubtypeOfBoth(selfType, variant);
          if (subtype != null) filteredVariants.add(subtype);
        }
        if (filteredVariants.size() == 0) {
          myIsValidByExclusion = false;
          return selfType;
        }
        if (filteredVariants.size() == 1) {
          return filteredVariants.iterator().next();
        }
        return null; // will be handled by variants
      }
      return selfType;
    }

    JsonSchemaType subtypeOfBoth = getSubtypeOfBoth(selfType, otherType);
    if (subtypeOfBoth == null) {
      myIsValidByExclusion = false;
      return otherType;
    }
    return subtypeOfBoth;
  }

  public Set<JsonSchemaType> mergeTypeVariantSets(@Nullable Set<JsonSchemaType> self, @Nullable Set<JsonSchemaType> other) {
    if (self == null) return other;
    if (other == null) return self;

    Set<JsonSchemaType> resultSet = EnumSet.noneOf(JsonSchemaType.class);
    for (JsonSchemaType type : self) {
      JsonSchemaType merged = mergeTypes(type, null, other);
      if (merged != null) resultSet.add(merged);
    }

    if (resultSet.isEmpty()) {
      myIsValidByExclusion = false;
      return other;
    }

    return resultSet;
  }

  //peer pointer is not merged!
  @Override
  public void mergeValues(@NotNull JsonSchemaObject otherBase) {
    var other = ((JsonSchemaObjectImpl)otherBase);
    // we do not copy id, schema
    mergeProperties(this, other);
    myDefinitionsMap = copyMap(myDefinitionsMap, other.getDefinitionsMap());
    final Map<String, ? extends JsonSchemaObject> map = copyMap(myPatternProperties == null ? null : myPatternProperties.mySchemasMap,
                                                                other.myPatternProperties == null
                                                                ? null
                                                                : other.myPatternProperties.mySchemasMap);
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
    if (!StringUtil.isEmptyOrSpaces(other.myDeprecationMessage)) {
      myDeprecationMessage = other.myDeprecationMessage;
    }

    myType = mergeTypes(myType, other.myType, other.myTypeVariants);

    if (other.myDefault != null) myDefault = other.myDefault;
    if (other.myExample != null) myExample = other.myExample;
    if (other.myRef != null) myRef = other.myRef;
    if (other.myFormat != null) myFormat = other.myFormat;
    myTypeVariants = mergeTypeVariantSets(myTypeVariants, other.myTypeVariants);
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
    if (other.myAdditionalPropertiesAllowed != null) {
      myAdditionalPropertiesAllowed = other.myAdditionalPropertiesAllowed;
      if (other.myAdditionalPropertiesAllowed == Boolean.FALSE) {
        addAdditionalPropsNotAllowedFor(other.myFileUrl, other.myPointer);
      }
    }
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
    if (myRequired != null && other.myRequired != null) {
      Set<String> set = new HashSet<>(myRequired.size() + other.myRequired.size());
      set.addAll(myRequired);
      set.addAll(other.myRequired);
      myRequired = set;
    }
    else if (other.myRequired != null) {
      myRequired = other.myRequired;
    }
    myPropertyDependencies = copyMap(myPropertyDependencies, other.myPropertyDependencies);
    mySchemaDependencies = copyMap(mySchemaDependencies, other.mySchemaDependencies);
    myEnumMetadata = copyMap(myEnumMetadata, other.myEnumMetadata);
    if (other.myEnum != null) myEnum = other.myEnum;
    myAllOf = copyList(myAllOf, other.myAllOf);
    myAnyOf = copyList(myAnyOf, other.myAnyOf);
    myOneOf = copyList(myOneOf, other.myOneOf);
    if (other.myNot != null) myNot = other.myNot;
    if (other.myIfThenElse != null) {
      if (myIfThenElse == null) {
        myIfThenElse = other.myIfThenElse;
      }
      else {
        myIfThenElse = ContainerUtil.concat(myIfThenElse, other.myIfThenElse);
      }
    }
    myShouldValidateAgainstJSType |= other.myShouldValidateAgainstJSType;
    if (myLanguageInjection == null) myLanguageInjection = other.myLanguageInjection;
    myForceCaseInsensitive = myForceCaseInsensitive || other.myForceCaseInsensitive;
  }

  public static void mergeProperties(@NotNull JsonSchemaObjectImpl thisObject, @NotNull JsonSchemaObject otherObject) {
    for (var prop : otherObject.getProperties().entrySet()) {
      String key = prop.getKey();
      var otherProp = prop.getValue();
      if (!(otherProp instanceof JsonSchemaObjectImpl impl)) continue;
      if (!thisObject.myProperties.containsKey(key)) {
        thisObject.myProperties.put(key, impl);
      }
      else {
        JsonSchemaObjectImpl existingProp = thisObject.myProperties.get(key);
        thisObject.myProperties.put(key, merge(existingProp, impl, ((JsonSchemaObjectImpl)otherProp)));
      }
    }
  }

  public void setShouldValidateAgainstJSType(boolean value) {
    myShouldValidateAgainstJSType = value;
  }

  @Override
  public boolean isShouldValidateAgainstJSType() {
    return myShouldValidateAgainstJSType;
  }

  public static @Nullable <T> List<T> copyList(@Nullable List<T> target, @Nullable List<T> source) {
    if (source == null || source.isEmpty()) return target;
    if (target == null) target = new ArrayList<>(source.size());
    target.addAll(source);
    return target;
  }

  public static @Nullable <K, V> Map<K, V> copyMap(@Nullable Map<K, V> target, @Nullable Map<K, V> source) {
    if (source == null || source.isEmpty()) return target;
    if (target == null) target = new HashMap<>(source.size());
    target.putAll(source);
    return target;
  }

  @Override
  public @Nullable Map<String, JsonSchemaObjectImpl> getDefinitionsMap() {
    return myDefinitionsMap;
  }

  public void setDefinitionsMap(@NotNull Map<String, JsonSchemaObjectImpl> definitionsMap) {
    myDefinitionsMap = definitionsMap;
  }

  @Override
  public @NotNull Map<String, JsonSchemaObjectImpl> getProperties() {
    return myProperties;
  }

  public void setProperties(@NotNull Map<String, JsonSchemaObjectImpl> properties) {
    myProperties = properties;
  }

  @Override
  public boolean hasPatternProperties() {
    return myPatternProperties != null;
  }

  public void setPatternProperties(@NotNull Map<String, JsonSchemaObjectImpl> patternProperties) {
    myPatternProperties = new PatternProperties(patternProperties);
  }

  public @Nullable PatternProperties getPatternProperties() {
    return myPatternProperties;
  }

  @Override
  public @Nullable JsonSchemaType getType() {
    return myType;
  }

  public void setType(@Nullable JsonSchemaType type) {
    myType = type;
  }

  @Override
  public @Nullable Number getMultipleOf() {
    return myMultipleOf;
  }

  public void setMultipleOf(@Nullable Number multipleOf) {
    myMultipleOf = multipleOf;
  }

  @Override
  public @Nullable Number getMaximum() {
    return myMaximum;
  }

  public void setMaximum(@Nullable Number maximum) {
    myMaximum = maximum;
  }

  @Override
  public boolean isExclusiveMaximum() {
    return myExclusiveMaximum;
  }

  @Override
  public @Nullable Number getExclusiveMaximumNumber() {
    return myExclusiveMaximumNumber;
  }

  public void setExclusiveMaximumNumber(@Nullable Number exclusiveMaximumNumber) {
    myExclusiveMaximumNumber = exclusiveMaximumNumber;
  }

  @Override
  public @Nullable Number getExclusiveMinimumNumber() {
    return myExclusiveMinimumNumber;
  }

  public void setExclusiveMinimumNumber(@Nullable Number exclusiveMinimumNumber) {
    myExclusiveMinimumNumber = exclusiveMinimumNumber;
  }

  public void setExclusiveMaximum(boolean exclusiveMaximum) {
    myExclusiveMaximum = exclusiveMaximum;
  }

  @Override
  public @Nullable Number getMinimum() {
    return myMinimum;
  }

  public void setMinimum(@Nullable Number minimum) {
    myMinimum = minimum;
  }

  @Override
  public boolean isExclusiveMinimum() {
    return myExclusiveMinimum;
  }

  public void setExclusiveMinimum(boolean exclusiveMinimum) {
    myExclusiveMinimum = exclusiveMinimum;
  }

  @Override
  public @Nullable Integer getMaxLength() {
    return myMaxLength;
  }

  public void setMaxLength(@Nullable Integer maxLength) {
    myMaxLength = maxLength;
  }

  @Override
  public @Nullable Integer getMinLength() {
    return myMinLength;
  }

  public void setMinLength(@Nullable Integer minLength) {
    myMinLength = minLength;
  }

  @Override
  public @Nullable String getPattern() {
    return myPattern == null ? null : myPattern.getPattern();
  }

  public void setPattern(@Nullable String pattern) {
    myPattern = pattern == null ? null : new PropertyNamePattern(pattern);
  }

  @Override
  public boolean getAdditionalPropertiesAllowed() {
    return myAdditionalPropertiesAllowed == null || myAdditionalPropertiesAllowed;
  }

  public void setAdditionalPropertiesAllowed(@Nullable Boolean additionalPropertiesAllowed) {
    myAdditionalPropertiesAllowed = additionalPropertiesAllowed;
    if (additionalPropertiesAllowed == Boolean.FALSE) {
      addAdditionalPropsNotAllowedFor(myFileUrl, myPointer);
    }
  }

  // for the sake of merging validation results, we need to know if this schema prohibits additional properties itself,
  // or if it inherits this prohibition flag from the merge result, as the behavior differs in these cases
  @Override
  public boolean hasOwnExtraPropertyProhibition() {
    return getAdditionalPropertiesAllowed() == Boolean.FALSE &&
           (myAdditionalPropertiesNotAllowedFor == null ||
            myAdditionalPropertiesNotAllowedFor.contains(myFileUrl + myPointer));
  }

  public void addAdditionalPropsNotAllowedFor(String url, String pointer) {
    Set<String> newSet = myAdditionalPropertiesNotAllowedFor == null
                         ? new HashSet<>()
                         : new HashSet<>(myAdditionalPropertiesNotAllowedFor);
    newSet.add(url + pointer);
    myAdditionalPropertiesNotAllowedFor = newSet;
  }

  @Override
  public @Nullable JsonSchemaObjectImpl getPropertyNamesSchema() {
    return myPropertyNamesSchema;
  }

  public void setPropertyNamesSchema(@Nullable JsonSchemaObjectImpl propertyNamesSchema) {
    myPropertyNamesSchema = propertyNamesSchema;
  }

  @Override
  public @Nullable JsonSchemaObject getUnevaluatedItemsSchema() {
    return null;
  }

  @Override
  public @Nullable JsonSchemaObjectImpl getAdditionalPropertiesSchema() {
    return myAdditionalPropertiesSchema;
  }

  @Override
  public @Nullable JsonSchemaObject getUnevaluatedPropertiesSchema() {
    return null;
  }

  public void setAdditionalPropertiesSchema(@Nullable JsonSchemaObjectImpl additionalPropertiesSchema) {
    myAdditionalPropertiesSchema = additionalPropertiesSchema;
  }

  @Override
  public @Nullable Boolean getAdditionalItemsAllowed() {
    return myAdditionalItemsAllowed == null || myAdditionalItemsAllowed;
  }

  public void setAdditionalItemsAllowed(@Nullable Boolean additionalItemsAllowed) {
    myAdditionalItemsAllowed = additionalItemsAllowed;
  }

  @Override
  public @Nullable String getDeprecationMessage() {
    return myDeprecationMessage;
  }

  public void setDeprecationMessage(@Nullable String deprecationMessage) {
    myDeprecationMessage = deprecationMessage;
  }

  @Override
  public @Nullable JsonSchemaObjectImpl getAdditionalItemsSchema() {
    return myAdditionalItemsSchema;
  }

  public void setAdditionalItemsSchema(@Nullable JsonSchemaObjectImpl additionalItemsSchema) {
    myAdditionalItemsSchema = additionalItemsSchema;
  }

  @Override
  public @Nullable JsonSchemaObjectImpl getItemsSchema() {
    return myItemsSchema;
  }

  public void setItemsSchema(@Nullable JsonSchemaObjectImpl itemsSchema) {
    myItemsSchema = itemsSchema;
  }

  @Override
  public @Nullable JsonSchemaObjectImpl getContainsSchema() {
    return myContainsSchema;
  }

  public void setContainsSchema(@Nullable JsonSchemaObjectImpl containsSchema) {
    myContainsSchema = containsSchema;
  }

  @Override
  public @Nullable List<JsonSchemaObjectImpl> getItemsSchemaList() {
    return myItemsSchemaList;
  }

  public void setItemsSchemaList(@Nullable List<JsonSchemaObjectImpl> itemsSchemaList) {
    myItemsSchemaList = itemsSchemaList;
  }

  @Override
  public @Nullable Integer getMaxItems() {
    return myMaxItems;
  }

  public void setMaxItems(@Nullable Integer maxItems) {
    myMaxItems = maxItems;
  }

  @Override
  public @Nullable Integer getMinItems() {
    return myMinItems;
  }

  public void setMinItems(@Nullable Integer minItems) {
    myMinItems = minItems;
  }

  @Override
  public boolean isUniqueItems() {
    return Boolean.TRUE.equals(myUniqueItems);
  }

  public void setUniqueItems(boolean uniqueItems) {
    myUniqueItems = uniqueItems;
  }

  @Override
  public @Nullable Integer getMaxProperties() {
    return myMaxProperties;
  }

  public void setMaxProperties(@Nullable Integer maxProperties) {
    myMaxProperties = maxProperties;
  }

  @Override
  public @Nullable Integer getMinProperties() {
    return myMinProperties;
  }

  public void setMinProperties(@Nullable Integer minProperties) {
    myMinProperties = minProperties;
  }

  @Override
  public @Nullable Set<String> getRequired() {
    return myRequired;
  }

  public void setRequired(@Nullable Set<String> required) {
    myRequired = required;
  }

  @Override
  public @Nullable Map<String, List<String>> getPropertyDependencies() {
    return myPropertyDependencies;
  }

  public void setPropertyDependencies(@Nullable Map<String, List<String>> propertyDependencies) {
    myPropertyDependencies = propertyDependencies;
  }

  @Override
  public @Nullable Map<String, JsonSchemaObjectImpl> getSchemaDependencies() {
    return mySchemaDependencies;
  }

  public void setSchemaDependencies(@Nullable Map<String, JsonSchemaObjectImpl> schemaDependencies) {
    mySchemaDependencies = schemaDependencies;
  }

  @Override
  public @Nullable Map<String, Map<String, String>> getEnumMetadata() {
    return myEnumMetadata;
  }

  public void setEnumMetadata(@Nullable Map<String, Map<String, String>> enumMetadata) {
    myEnumMetadata = enumMetadata;
  }

  @Override
  public @Nullable List<Object> getEnum() {
    return myEnum;
  }

  public void setEnum(@Nullable List<Object> anEnum) {
    myEnum = anEnum;
  }

  @Override
  public @Nullable List<JsonSchemaObjectImpl> getAllOf() {
    return myAllOf;
  }

  public void setAllOf(@Nullable List<JsonSchemaObjectImpl> allOf) {
    myAllOf = allOf;
  }

  @Override
  public @Nullable List<JsonSchemaObjectImpl> getAnyOf() {
    return myAnyOf;
  }

  public void setAnyOf(@Nullable List<JsonSchemaObjectImpl> anyOf) {
    myAnyOf = anyOf;
  }

  @Override
  public @Nullable List<JsonSchemaObjectImpl> getOneOf() {
    return myOneOf;
  }

  public void setOneOf(@Nullable List<JsonSchemaObjectImpl> oneOf) {
    myOneOf = oneOf;
  }

  @Override
  public @Nullable JsonSchemaObjectImpl getNot() {
    return myNot;
  }

  public void setNot(@Nullable JsonSchemaObjectImpl not) {
    myNot = not;
  }

  @Override
  public @Nullable List<IfThenElse> getIfThenElse() {
    return myIfThenElse;
  }

  public void setIfThenElse(@Nullable List<IfThenElse> ifThenElse) {
    myIfThenElse = ifThenElse;
  }

  public void setIf(@Nullable JsonSchemaObjectImpl anIf) {
    myIf = anIf;
  }

  public void setThen(@Nullable JsonSchemaObjectImpl then) {
    myThen = then;
  }

  public void setElse(@Nullable JsonSchemaObjectImpl anElse) {
    myElse = anElse;
  }

  @Override
  public @Nullable Set<JsonSchemaType> getTypeVariants() {
    return myTypeVariants;
  }

  public void setTypeVariants(@Nullable Set<JsonSchemaType> typeVariants) {
    myTypeVariants = typeVariants;
  }

  @Override
  public @Nullable String getRef() {
    return myRef;
  }

  public void setRef(@Nullable String ref) {
    myRef = ref;
  }

  public void setRefRecursive(boolean isRecursive) {
    myRefIsRecursive = isRecursive;
  }

  @Override
  public boolean isRefRecursive() {
    return myRefIsRecursive;
  }

  public void setRecursiveAnchor(boolean isRecursive) {
    myIsRecursiveAnchor = isRecursive;
  }

  @Override
  public boolean isRecursiveAnchor() {
    return myIsRecursiveAnchor;
  }

  public void setBackReference(JsonSchemaObjectImpl object) {
    myBackRef = object;
  }

  @Override
  public JsonSchemaObjectImpl getBackReference() {
    return myBackRef;
  }

  @Override
  public @Nullable Object getDefault() {
    if (JsonSchemaType._integer.equals(myType)) return myDefault instanceof Number ? ((Number)myDefault).intValue() : myDefault;
    return myDefault;
  }

  public void setDefault(@Nullable Object aDefault) {
    myDefault = aDefault;
  }

  @Override
  public @Nullable Map<String, Object> getExample() {
    return myExample;
  }


  @Override
  public @Nullable JsonSchemaObject getExampleByName(@NotNull String name) {
    return null;
  }

  public void setExample(@Nullable Map<String, Object> example) {
    myExample = example;
  }

  @Override
  public @Nullable String getFormat() {
    return myFormat;
  }

  public void setFormat(@Nullable String format) {
    myFormat = format;
  }

  @Override
  public @Nullable String getId() {
    return myId;
  }

  public void setId(@Nullable String id) {
    if (id == null) {
      myId = null;
    }
    else {
      myId = JsonPointerUtil.normalizeId(id);
    }
  }

  @Override
  public @Nullable String getSchema() {
    return mySchema;
  }

  public void setSchema(@Nullable String schema) {
    mySchema = schema;
  }

  @Override
  public @Nullable String getDescription() {
    return myDescription;
  }

  public void setDescription(@NotNull String description) {
    myDescription = unescapeJsonString(description);
  }

  @Override
  public @Nullable String getHtmlDescription() {
    return myHtmlDescription;
  }

  public void setHtmlDescription(@NotNull String htmlDescription) {
    myHtmlDescription = unescapeJsonString(htmlDescription);
  }

  @Override
  public @Nullable String getTitle() {
    return myTitle;
  }

  public void setTitle(@NotNull String title) {
    myTitle = unescapeJsonString(title);
  }

  public static String unescapeJsonString(final @NotNull String text) {
    return StringUtil.unescapeStringCharacters(text);
  }

  @Override
  public @Nullable JsonSchemaObjectImpl getMatchingPatternPropertySchema(@NotNull String name) {
    if (myPatternProperties == null) return null;
    return (JsonSchemaObjectImpl)myPatternProperties.getPatternPropertySchema(name);
  }

  @Override
  public @NotNull Iterator<String> getSchemaDependencyNames() {
    var dependencies = getSchemaDependencies();
    return dependencies == null ? Collections.emptyIterator() : dependencies.keySet().iterator();
  }

  @Override
  public @Nullable JsonSchemaObject getSchemaDependencyByName(@NotNull String name) {
    var dependencies = getSchemaDependencies();
    return dependencies == null ? null : dependencies.get(name);
  }

  @Override
  public @Nullable JsonSchemaObject getDefinitionByName(@NotNull String name) {
    var map = getDefinitionsMap();
    return map == null ? null : map.get(name);
  }

  @Override
  public @NotNull Iterator<String> getDefinitionNames() {
    var map = getDefinitionsMap();
    return map == null ? Collections.emptyIterator() : map.keySet().iterator();
  }

  @Override
  public @NotNull Iterator<String> getPropertyNames() {
    return getProperties().keySet().iterator();
  }

  @Override
  public @Nullable JsonSchemaObject getPropertyByName(@NotNull String name) {
    return getProperties().get(name);
  }

  @Override
  public boolean checkByPattern(@NotNull String value) {
    return myPattern != null && myPattern.checkByPattern(value);
  }

  @Override
  public @Nullable String getPatternError() {
    return myPattern == null ? null : myPattern.getPatternError();
  }

  @Override
  public @Nullable JsonSchemaObject findRelativeDefinition(@NotNull String ref) {
    return JsonSchemaObjectReadingUtils.findRelativeDefinition(this, ref);
  }

  @Override
  public @Nullable JsonSchemaObject resolveRefSchema(@NotNull JsonSchemaService service) {
    return JsonSchemaObjectReadingUtils.resolveRefSchema(this, service);
  }

  public ConcurrentMap<String, JsonSchemaObject> getComputedRefsStorage(@NotNull Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(
      myUserDataHolder,
      () -> CachedValueProvider.Result.create(new ConcurrentHashMap<>(), JsonDependencyModificationTracker.forProject(project))
    );
  }

  public static @NotNull JsonSchemaObjectImpl merge(@NotNull JsonSchemaObjectImpl base,
                                                    @NotNull JsonSchemaObjectImpl other,
                                                    @NotNull JsonSchemaObjectImpl pointTo) {
    final JsonSchemaObjectImpl object = new JsonSchemaObjectImpl(pointTo.myRawFile, pointTo.myFileUrl, pointTo.getPointer());
    object.mergeValues(other);
    object.mergeValues(base);
    object.setRef(other.getRef());
    return object;
  }
}
