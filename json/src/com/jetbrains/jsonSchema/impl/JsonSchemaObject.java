// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.openapi.vfs.impl.http.RemoteFileInfo;
import com.intellij.openapi.vfs.impl.http.RemoteFileState;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.JsonDependencyModificationTracker;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.nestedCompletions.NestedCompletionsNode;
import com.jetbrains.jsonSchema.impl.nestedCompletions.NestedCompletionsNodeBuilder;
import com.jetbrains.jsonSchema.impl.nestedCompletions.NestedCompletionsNodeKt;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jetbrains.jsonSchema.JsonPointerUtil.*;

public final class JsonSchemaObject {
  private static final Logger LOG = Logger.getInstance(JsonSchemaObject.class);

  public static final String MOCK_URL = "mock:///";
  public static final String TEMP_URL = "temp:///";
  public static final @NonNls String DEFINITIONS = "definitions";
  public static final @NonNls String DEFINITIONS_v9 = "$defs";
  public static final @NonNls String PROPERTIES = "properties";
  public static final @NonNls String ITEMS = "items";
  public static final @NonNls String ADDITIONAL_ITEMS = "additionalItems";
  public static final @NonNls String X_INTELLIJ_HTML_DESCRIPTION = "x-intellij-html-description";
  public static final @NonNls String X_INTELLIJ_LANGUAGE_INJECTION = "x-intellij-language-injection";
  public static final @NonNls String X_INTELLIJ_CASE_INSENSITIVE = "x-intellij-case-insensitive";
  public static final @NonNls String X_INTELLIJ_ENUM_METADATA = "x-intellij-enum-metadata";
  private final @Nullable String myFileUrl;
  private @Nullable JsonSchemaObject myBackRef;
  private final @NotNull String myPointer;
  private final @Nullable VirtualFile myRawFile;
  private @Nullable Map<String, JsonSchemaObject> myDefinitionsMap;
  public static final @NotNull JsonSchemaObject NULL_OBJ = new JsonSchemaObject("$_NULL_$");
  private @NotNull Map<String, JsonSchemaObject> myProperties;

  private @Nullable PatternProperties myPatternProperties;
  private @Nullable PropertyNamePattern myPattern;

  private @Nullable String myId;
  private @Nullable String mySchema;

  private @Nullable String myTitle;
  private @Nullable String myDescription;
  private @Nullable String myHtmlDescription;
  private @Nullable String myLanguageInjection;
  private @Nullable String myLanguageInjectionPrefix;
  private @Nullable String myLanguageInjectionPostfix;

  private @Nullable JsonSchemaType myType;
  private @Nullable Object myDefault;
  private @Nullable Map<String, Object> myExample;
  private @Nullable String myRef;
  private boolean myRefIsRecursive;
  private boolean myIsRecursiveAnchor;
  private @Nullable String myFormat;
  private @Nullable Set<JsonSchemaType> myTypeVariants;
  private @Nullable Number myMultipleOf;
  private @Nullable Number myMaximum;
  private boolean myExclusiveMaximum;
  private @Nullable Number myExclusiveMaximumNumber;
  private @Nullable Number myMinimum;
  private boolean myExclusiveMinimum;
  private @Nullable Number myExclusiveMinimumNumber;
  private @Nullable Integer myMaxLength;
  private @Nullable Integer myMinLength;

  private @Nullable Boolean myAdditionalPropertiesAllowed;
  private @Nullable Set<String> myAdditionalPropertiesNotAllowedFor;
  private @Nullable JsonSchemaObject myAdditionalPropertiesSchema;
  private @Nullable JsonSchemaObject myPropertyNamesSchema;

  private @Nullable Boolean myAdditionalItemsAllowed;
  private @Nullable JsonSchemaObject myAdditionalItemsSchema;

  private @Nullable JsonSchemaObject myItemsSchema;
  private @Nullable JsonSchemaObject myContainsSchema;
  private @Nullable List<JsonSchemaObject> myItemsSchemaList;

  private @Nullable Integer myMaxItems;
  private @Nullable Integer myMinItems;

  private @Nullable Boolean myUniqueItems;

  private @Nullable Integer myMaxProperties;
  private @Nullable Integer myMinProperties;
  private @Nullable Set<String> myRequired;

  private @Nullable Map<String, List<String>> myPropertyDependencies;
  private @Nullable Map<String, JsonSchemaObject> mySchemaDependencies;

  private @Nullable List<Object> myEnum;

  private @Nullable List<JsonSchemaObject> myAllOf;
  private @Nullable List<JsonSchemaObject> myAnyOf;
  private @Nullable List<JsonSchemaObject> myOneOf;
  private @Nullable JsonSchemaObject myNot;
  private @Nullable List<IfThenElse> myIfThenElse;
  private @Nullable JsonSchemaObject myIf;
  private @Nullable JsonSchemaObject myThen;
  private @Nullable JsonSchemaObject myElse;
  private boolean myShouldValidateAgainstJSType;

  private @Nullable String myDeprecationMessage;
  private @Nullable Map<String, String> myIdsMap;

  private @Nullable Map<String, Map<String, String>> myEnumMetadata;

  private boolean myForceCaseInsensitive = false;

  /** @see JsonSchemaObject#applyBuilderOnNestedCompletionsRoot(Function1) */
  private NestedCompletionsNode myNestedCompletionRoot = null;

  private final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();

  public boolean isValidByExclusion() {
    return myIsValidByExclusion;
  }

  public boolean isForceCaseInsensitive() {
    return myForceCaseInsensitive;
  }

  public void setForceCaseInsensitive(boolean forceCaseInsensitive) {
    myForceCaseInsensitive = forceCaseInsensitive;
  }

  private boolean myIsValidByExclusion = true;

  public JsonSchemaObject(@Nullable VirtualFile file, @NotNull String pointer) {
    myFileUrl = file == null ? null : file.getUrl();
    myRawFile = myFileUrl != null && JsonFileResolver.isTempOrMockUrl(myFileUrl) ? file : null;
    myPointer = pointer;
    myProperties = new HashMap<>();
  }

  private JsonSchemaObject(@Nullable VirtualFile rawFile, @Nullable String fileUrl, @NotNull String pointer) {
    myFileUrl = fileUrl;
    myRawFile = rawFile;
    myPointer = pointer;
    myProperties = new HashMap<>();
  }

  private JsonSchemaObject(@NotNull String pointer) {
    this(null, pointer);
  }

  public void completeInitialization(JsonValueAdapter jsonObject) {
    if (myIf != null) {
      myIfThenElse = new ArrayList<>();
      myIfThenElse.add(new IfThenElse(myIf, myThen, myElse));
    }

    myIdsMap = JsonCachedValues.getOrComputeIdsMap(jsonObject.getDelegate().getContainingFile());
  }

  public String resolveId(@NotNull String id) {
    return myIdsMap == null ? null : myIdsMap.get(id);
  }

  public @NotNull String getPointer() {
    return myPointer;
  }

  public @Nullable String getFileUrl() {
    return myFileUrl;
  }

  /**
   * NOTE: Raw files are stored only in very specific cases such as mock files
   * This API should be used only as a fallback to trying to resolve file via its url returned by getFileUrl()
   */
  public @Nullable VirtualFile getRawFile() {
    return myRawFile;
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

  public @Nullable String getLanguageInjection() {
    return myLanguageInjection;
  }

  public @Nullable String getLanguageInjectionPrefix() {
    return myLanguageInjectionPrefix;
  }

  public @Nullable String getLanguageInjectionPostfix() {
    return myLanguageInjectionPostfix;
  }

  /**
   * Builds the nested completions root tree.
   * @see NestedCompletionsNodeKt#buildNestedCompletionsRootTree(JsonSchemaObject, Function1) to see how the DSL is ideally used in Kotlin
   */
  @ApiStatus.Experimental
  public void applyBuilderOnNestedCompletionsRoot(Function1<? super NestedCompletionsNodeBuilder, Unit> builder) {
    myNestedCompletionRoot = NestedCompletionsNodeKt.buildNestedCompletionsTree(builder);
  }

  NestedCompletionsNode getNestedCompletionRoot() {
    return myNestedCompletionRoot;
  }

  private static @Nullable JsonSchemaType getSubtypeOfBoth(@NotNull JsonSchemaType selfType,
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

  private @Nullable JsonSchemaType mergeTypes(@Nullable JsonSchemaType selfType,
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
    if (subtypeOfBoth == null){
      myIsValidByExclusion = false;
      return otherType;
    }
    return subtypeOfBoth;
  }

  private Set<JsonSchemaType> mergeTypeVariantSets(@Nullable Set<JsonSchemaType> self, @Nullable Set<JsonSchemaType> other) {
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
      if (myIfThenElse == null) myIfThenElse = other.myIfThenElse;
      else myIfThenElse = ContainerUtil.concat(myIfThenElse, other.myIfThenElse);
    }
    myShouldValidateAgainstJSType |= other.myShouldValidateAgainstJSType;
    if (myLanguageInjection == null) myLanguageInjection = other.myLanguageInjection;
    myForceCaseInsensitive = myForceCaseInsensitive || other.myForceCaseInsensitive;
    myNestedCompletionRoot = NestedCompletionsNodeKt.merge(myNestedCompletionRoot, other.myNestedCompletionRoot);
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
        thisObject.myProperties.put(key, merge(existingProp, otherProp, otherProp));
      }
    }
  }

  public void setShouldValidateAgainstJSType() {
    myShouldValidateAgainstJSType = true;
  }

  public boolean isShouldValidateAgainstJSType() {
    return myShouldValidateAgainstJSType;
  }

  private static @Nullable <T> List<T> copyList(@Nullable List<T> target, @Nullable List<T> source) {
    if (source == null || source.isEmpty()) return target;
    if (target == null) target = new ArrayList<>(source.size());
    target.addAll(source);
    return target;
  }

  private static @Nullable <K, V> Map<K, V> copyMap(@Nullable Map<K, V> target, @Nullable Map<K, V> source) {
    if (source == null || source.isEmpty()) return target;
    if (target == null) target = new HashMap<>(source.size());
    target.putAll(source);
    return target;
  }

  public @Nullable Map<String, JsonSchemaObject> getDefinitionsMap() {
    return myDefinitionsMap;
  }

  public void setDefinitionsMap(@NotNull Map<String, JsonSchemaObject> definitionsMap) {
    myDefinitionsMap = definitionsMap;
  }

  public @NotNull Map<String, JsonSchemaObject> getProperties() {
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

  public @Nullable JsonSchemaType getType() {
    return myType;
  }

  public void setType(@Nullable JsonSchemaType type) {
    myType = type;
  }

  public @Nullable Number getMultipleOf() {
    return myMultipleOf;
  }

  public void setMultipleOf(@Nullable Number multipleOf) {
    myMultipleOf = multipleOf;
  }

  public @Nullable Number getMaximum() {
    return myMaximum;
  }

  public void setMaximum(@Nullable Number maximum) {
    myMaximum = maximum;
  }

  public boolean isExclusiveMaximum() {
    return myExclusiveMaximum;
  }

  public @Nullable Number getExclusiveMaximumNumber() {
    return myExclusiveMaximumNumber;
  }

  public void setExclusiveMaximumNumber(@Nullable Number exclusiveMaximumNumber) {
    myExclusiveMaximumNumber = exclusiveMaximumNumber;
  }

  public @Nullable Number getExclusiveMinimumNumber() {
    return myExclusiveMinimumNumber;
  }

  public void setExclusiveMinimumNumber(@Nullable Number exclusiveMinimumNumber) {
    myExclusiveMinimumNumber = exclusiveMinimumNumber;
  }

  public void setExclusiveMaximum(boolean exclusiveMaximum) {
    myExclusiveMaximum = exclusiveMaximum;
  }

  public @Nullable Number getMinimum() {
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

  public @Nullable Integer getMaxLength() {
    return myMaxLength;
  }

  public void setMaxLength(@Nullable Integer maxLength) {
    myMaxLength = maxLength;
  }

  public @Nullable Integer getMinLength() {
    return myMinLength;
  }

  public void setMinLength(@Nullable Integer minLength) {
    myMinLength = minLength;
  }

  public @Nullable String getPattern() {
    return myPattern == null ? null : myPattern.getPattern();
  }

  public void setPattern(@Nullable String pattern) {
    myPattern = pattern == null ? null : new PropertyNamePattern(pattern);
  }

  public @Nullable Boolean getAdditionalPropertiesAllowed() {
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
  public boolean hasOwnExtraPropertyProhibition() {
    return getAdditionalPropertiesAllowed() == Boolean.FALSE &&
           (myAdditionalPropertiesNotAllowedFor == null ||
            myAdditionalPropertiesNotAllowedFor.contains(myFileUrl + myPointer));
  }

  private void addAdditionalPropsNotAllowedFor(String url, String pointer) {
    Set<String> newSet = myAdditionalPropertiesNotAllowedFor == null
                             ? new HashSet<>()
                             : new HashSet<>(myAdditionalPropertiesNotAllowedFor);
    newSet.add(url + pointer);
    myAdditionalPropertiesNotAllowedFor = newSet;
  }

  public @Nullable JsonSchemaObject getPropertyNamesSchema() {
    return myPropertyNamesSchema;
  }

  public void setPropertyNamesSchema(@Nullable JsonSchemaObject propertyNamesSchema) {
    myPropertyNamesSchema = propertyNamesSchema;
  }

  public @Nullable JsonSchemaObject getAdditionalPropertiesSchema() {
    return myAdditionalPropertiesSchema;
  }

  public void setAdditionalPropertiesSchema(@Nullable JsonSchemaObject additionalPropertiesSchema) {
    myAdditionalPropertiesSchema = additionalPropertiesSchema;
  }

  public @Nullable Boolean getAdditionalItemsAllowed() {
    return myAdditionalItemsAllowed == null || myAdditionalItemsAllowed;
  }

  public void setAdditionalItemsAllowed(@Nullable Boolean additionalItemsAllowed) {
    myAdditionalItemsAllowed = additionalItemsAllowed;
  }

  public @Nullable String getDeprecationMessage() {
    return myDeprecationMessage;
  }

  public void setDeprecationMessage(@Nullable String deprecationMessage) {
    myDeprecationMessage = deprecationMessage;
  }

  public @Nullable JsonSchemaObject getAdditionalItemsSchema() {
    return myAdditionalItemsSchema;
  }

  public void setAdditionalItemsSchema(@Nullable JsonSchemaObject additionalItemsSchema) {
    myAdditionalItemsSchema = additionalItemsSchema;
  }

  public @Nullable JsonSchemaObject getItemsSchema() {
    return myItemsSchema;
  }

  public void setItemsSchema(@Nullable JsonSchemaObject itemsSchema) {
    myItemsSchema = itemsSchema;
  }

  public @Nullable JsonSchemaObject getContainsSchema() {
    return myContainsSchema;
  }

  public void setContainsSchema(@Nullable JsonSchemaObject containsSchema) {
    myContainsSchema = containsSchema;
  }

  public @Nullable List<JsonSchemaObject> getItemsSchemaList() {
    return myItemsSchemaList;
  }

  public void setItemsSchemaList(@Nullable List<JsonSchemaObject> itemsSchemaList) {
    myItemsSchemaList = itemsSchemaList;
  }

  public @Nullable Integer getMaxItems() {
    return myMaxItems;
  }

  public void setMaxItems(@Nullable Integer maxItems) {
    myMaxItems = maxItems;
  }

  public @Nullable Integer getMinItems() {
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

  public @Nullable Integer getMaxProperties() {
    return myMaxProperties;
  }

  public void setMaxProperties(@Nullable Integer maxProperties) {
    myMaxProperties = maxProperties;
  }

  public @Nullable Integer getMinProperties() {
    return myMinProperties;
  }

  public void setMinProperties(@Nullable Integer minProperties) {
    myMinProperties = minProperties;
  }

  public @Nullable Set<String> getRequired() {
    return myRequired;
  }

  public void setRequired(@Nullable Set<String> required) {
    myRequired = required;
  }

  public @Nullable Map<String, List<String>> getPropertyDependencies() {
    return myPropertyDependencies;
  }

  public void setPropertyDependencies(@Nullable Map<String, List<String>> propertyDependencies) {
    myPropertyDependencies = propertyDependencies;
  }

  public @Nullable Map<String, JsonSchemaObject> getSchemaDependencies() {
    return mySchemaDependencies;
  }

  public void setSchemaDependencies(@Nullable Map<String, JsonSchemaObject> schemaDependencies) {
    mySchemaDependencies = schemaDependencies;
  }

  public @Nullable Map<String, Map<String, String>> getEnumMetadata() {
    return myEnumMetadata;
  }

  public void setEnumMetadata(@Nullable Map<String, Map<String, String>> enumMetadata) {
    myEnumMetadata = enumMetadata;
  }

  public @Nullable List<Object> getEnum() {
    return myEnum;
  }

  public void setEnum(@Nullable List<Object> anEnum) {
    myEnum = anEnum;
  }

  public @Nullable List<JsonSchemaObject> getAllOf() {
    return myAllOf;
  }

  public void setAllOf(@Nullable List<JsonSchemaObject> allOf) {
    myAllOf = allOf;
  }

  public @Nullable List<JsonSchemaObject> getAnyOf() {
    return myAnyOf;
  }

  public void setAnyOf(@Nullable List<JsonSchemaObject> anyOf) {
    myAnyOf = anyOf;
  }

  public @Nullable List<JsonSchemaObject> getOneOf() {
    return myOneOf;
  }

  public void setOneOf(@Nullable List<JsonSchemaObject> oneOf) {
    myOneOf = oneOf;
  }

  public @Nullable JsonSchemaObject getNot() {
    return myNot;
  }

  public void setNot(@Nullable JsonSchemaObject not) {
    myNot = not;
  }

  public @Nullable List<IfThenElse> getIfThenElse() {
    return myIfThenElse;
  }

  public void setIf(@Nullable JsonSchemaObject anIf) {
    myIf = anIf;
  }

  public void setThen(@Nullable JsonSchemaObject then) {
    myThen = then;
  }

  public void setElse(@Nullable JsonSchemaObject anElse) {
    myElse = anElse;
  }

  public @Nullable Set<JsonSchemaType> getTypeVariants() {
    return myTypeVariants;
  }

  public void setTypeVariants(@Nullable Set<JsonSchemaType> typeVariants) {
    myTypeVariants = typeVariants;
  }

  public @Nullable String getRef() {
    return myRef;
  }

  public void setRef(@Nullable String ref) {
    myRef = ref;
  }

  public void setRefRecursive(boolean isRecursive) {
    myRefIsRecursive = isRecursive;
  }

  public boolean isRefRecursive() {
    return myRefIsRecursive;
  }

  public void setRecursiveAnchor(boolean isRecursive) {
    myIsRecursiveAnchor = isRecursive;
  }

  public boolean isRecursiveAnchor() {
    return myIsRecursiveAnchor;
  }

  public void setBackReference(JsonSchemaObject object) {
    myBackRef = object;
  }

  public @Nullable Object getDefault() {
    if (JsonSchemaType._integer.equals(myType)) return myDefault instanceof Number ? ((Number)myDefault).intValue() : myDefault;
    return myDefault;
  }

  public void setDefault(@Nullable Object aDefault) {
    myDefault = aDefault;
  }

  public @Nullable Map<String, Object> getExample() {
    return myExample;
  }

  public void setExample(@Nullable Map<String, Object> example) {
    myExample = example;
  }

  public @Nullable String getFormat() {
    return myFormat;
  }

  public void setFormat(@Nullable String format) {
    myFormat = format;
  }

  public @Nullable String getId() {
    return myId;
  }

  public void setId(@Nullable String id) {
    myId = id;
  }

  public @Nullable String getSchema() {
    return mySchema;
  }

  public void setSchema(@Nullable String schema) {
    mySchema = schema;
  }

  public @Nullable String getDescription() {
    return myDescription;
  }

  public void setDescription(@NotNull String description) {
    myDescription = unescapeJsonString(description);
  }

  public @Nullable String getHtmlDescription() {
    return myHtmlDescription;
  }

  public void setHtmlDescription(@NotNull String htmlDescription) {
    myHtmlDescription = unescapeJsonString(htmlDescription);
  }

  public @Nullable String getTitle() {
    return myTitle;
  }

  public void setTitle(@NotNull String title) {
    myTitle = unescapeJsonString(title);
  }

  private static String unescapeJsonString(final @NotNull String text) {
    return StringUtil.unescapeStringCharacters(text);
  }

  public @Nullable JsonSchemaObject getMatchingPatternPropertySchema(@NotNull String name) {
    if (myPatternProperties == null) return null;
    return myPatternProperties.getPatternPropertySchema(name);
  }

  public boolean checkByPattern(@NotNull String value) {
    return myPattern != null && myPattern.checkByPattern(value);
  }

  public @Nullable String getPatternError() {
    return myPattern == null ? null : myPattern.getPatternError();
  }

  public @Nullable JsonSchemaObject findRelativeDefinition(@NotNull String ref) {
    if (isSelfReference(ref)) {
      return this;
    }
    if (!ref.startsWith("#/")) {
      return null;
    }
    ref = ref.substring(2);
    final List<String> parts = split(ref);
    JsonSchemaObject current = this;
    for (int i = 0; i < parts.size(); i++) {
      if (current == null) return null;
      final String part = parts.get(i);
      if (DEFINITIONS.equals(part) || DEFINITIONS_v9.equals(part)) {
        if (i == (parts.size() - 1)) return null;
        //noinspection AssignmentToForLoopParameter
        final String nextPart = parts.get(++i);
        current = current.getDefinitionsMap() == null ? null : current.getDefinitionsMap().get(unescapeJsonPointerPart(nextPart));
        continue;
      }
      if (PROPERTIES.equals(part)) {
        if (i == (parts.size() - 1)) return null;
        //noinspection AssignmentToForLoopParameter
        current = current.getProperties().get(unescapeJsonPointerPart(parts.get(++i)));
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

  private static @Nullable Integer tryParseInt(String s) {
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

    return Objects.equals(myFileUrl, object.myFileUrl) && Objects.equals(myPointer, object.myPointer);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myFileUrl, myPointer);
  }

  private static @NotNull String adaptSchemaPattern(String pattern) {
    pattern = pattern.startsWith("^") || pattern.startsWith("*") || pattern.startsWith(".") ? pattern : (".*" + pattern);
    pattern = pattern.endsWith("+") || pattern.endsWith("*") || pattern.endsWith("$") ? pattern : (pattern + ".*");
    pattern = pattern.replace("\\\\", "\\");
    return pattern;
  }


  private static Pair<Pattern, String> compilePattern(final @NotNull String pattern) {
    try {
      return Pair.create(Pattern.compile(adaptSchemaPattern(pattern)), null);
    } catch (PatternSyntaxException e) {
      return Pair.create(null, e.getMessage());
    }
  }

  public static boolean matchPattern(final @NotNull Pattern pattern, final @NotNull String s) {
    try {
      return pattern.matcher(StringUtil.newBombedCharSequence(s, 300)).matches();
    } catch (ProcessCanceledException e) {
      // something wrong with the pattern, infinite cycle?
      Logger.getInstance(JsonSchemaObject.class).info("Pattern matching canceled");
      return false;
    } catch (Exception e) {
      // catch exceptions around to prevent things like:
      // https://bugs.openjdk.org/browse/JDK-6984178
      Logger.getInstance(JsonSchemaObject.class).info(e);
      return false;
    }
  }

  public @Nullable String getTypeDescription(boolean shortDesc) {
    JsonSchemaType type = getType();
    if (type != null) return type.getDescription();

    Set<JsonSchemaType> possibleTypes = getTypeVariants();

    String description = getTypesDescription(shortDesc, possibleTypes);
    if (description != null) return description;

    List<Object> anEnum = getEnum();
    if (anEnum != null) {
      return shortDesc ? "enum" : anEnum.stream().map(o -> o.toString()).collect(Collectors.joining(" | "));
    }

    JsonSchemaType guessedType = guessType();
    if (guessedType != null) {
      return guessedType.getDescription();
    }

    return null;
  }

  public @Nullable JsonSchemaType guessType() {
    // if we have an explicit type, here we are
    JsonSchemaType type = getType();
    if (type != null) return type;

    // process type variants before heuristic type detection
    final Set<JsonSchemaType> typeVariants = getTypeVariants();
    if (typeVariants != null) {
      final int size = typeVariants.size();
      if (size == 1) {
        return typeVariants.iterator().next();
      }
      else if (size >= 2) {
        return null;
      }
    }

    // heuristic type detection based on the set of applied constraints
    boolean hasObjectChecks = hasObjectChecks();
    boolean hasNumericChecks = hasNumericChecks();
    boolean hasStringChecks = hasStringChecks();
    boolean hasArrayChecks = hasArrayChecks();

    if (hasObjectChecks && !hasNumericChecks && !hasStringChecks && !hasArrayChecks) {
      return JsonSchemaType._object;
    }
    if (!hasObjectChecks && hasNumericChecks && !hasStringChecks && !hasArrayChecks) {
      return JsonSchemaType._number;
    }
    if (!hasObjectChecks && !hasNumericChecks && hasStringChecks && !hasArrayChecks) {
      return JsonSchemaType._string;
    }
    if (!hasObjectChecks && !hasNumericChecks && !hasStringChecks && hasArrayChecks) {
      return JsonSchemaType._array;
    }
    return null;
  }

  public boolean hasNumericChecks() {
    return getMultipleOf() != null
           || getExclusiveMinimumNumber() != null
           || getExclusiveMaximumNumber() != null
           || getMaximum() != null
           || getMinimum() != null;
  }

  public boolean hasStringChecks() {
    return getPattern() != null || getFormat() != null;
  }

  public boolean hasArrayChecks() {
    return isUniqueItems()
           || getContainsSchema() != null
           || getItemsSchema() != null
           || getItemsSchemaList() != null
           || getMinItems() != null
           || getMaxItems() != null;
  }

  public boolean hasObjectChecks() {
    return !getProperties().isEmpty()
           || getPropertyNamesSchema() != null
           || getPropertyDependencies() != null
           || hasPatternProperties()
           || getRequired() != null
           || getMinProperties() != null
           || getMaxProperties() != null;
  }

  static @Nullable String getTypesDescription(boolean shortDesc, @Nullable Collection<JsonSchemaType> possibleTypes) {
    if (possibleTypes == null || possibleTypes.size() == 0) return null;
    if (possibleTypes.size() == 1) return possibleTypes.iterator().next().getDescription();
    if (possibleTypes.contains(JsonSchemaType._any)) return JsonSchemaType._any.getDescription();

    Stream<String> typeDescriptions = possibleTypes.stream().map(t -> t.getDescription()).distinct().sorted();
    boolean isShort = false;
    if (shortDesc) {
      typeDescriptions = typeDescriptions.limit(3);
      if (possibleTypes.size() > 3) isShort = true;
    }
    return typeDescriptions.collect(Collectors.joining(" | ", "", isShort ? "| ..." : ""));
  }

  public @Nullable JsonSchemaObject resolveRefSchema(@NotNull JsonSchemaService service) {
    final String ref = getRef();
    assert !StringUtil.isEmptyOrSpaces(ref);
    ConcurrentMap<String, JsonSchemaObject> refsStorage = getComputedRefsStorage(service.getProject());
    JsonSchemaObject schemaObject = refsStorage.getOrDefault(ref, NULL_OBJ);
    if (schemaObject == NULL_OBJ) {
      JsonSchemaObject value = fetchSchemaFromRefDefinition(ref, this, service, isRefRecursive());
      if (!JsonFileResolver.isHttpPath(ref)) {
        service.registerReference(ref);
      }
      else if (value != null) {
        // our aliases - if http ref actually refers to a local file with specific ID
        VirtualFile virtualFile = service.resolveSchemaFile(value);
        if (virtualFile != null && !(virtualFile instanceof HttpVirtualFile)) {
          service.registerReference(virtualFile.getName());
        }
      }
      if (value != null && value != NULL_OBJ && !Objects.equals(value.myFileUrl, myFileUrl)) {
        value.setBackReference(this);
      }
      refsStorage.put(ref, value == null ? NULL_OBJ : value);
      return value;
    }
    return schemaObject;
  }

  private ConcurrentMap<String, JsonSchemaObject> getComputedRefsStorage(@NotNull Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(
      myUserDataHolder,
      () -> CachedValueProvider.Result.create(new ConcurrentHashMap<String, JsonSchemaObject>(), JsonDependencyModificationTracker.forProject(project))
    );
  }

  private static @Nullable JsonSchemaObject fetchSchemaFromRefDefinition(@NotNull String ref,
                                                                         final @NotNull JsonSchemaObject schema,
                                                                         @NotNull JsonSchemaService service,
                                                                         boolean recursive) {

    final VirtualFile schemaFile = service.resolveSchemaFile(schema);
    if (schemaFile == null) return null;
    final JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter splitter = new JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter(ref);
    String schemaId = splitter.getSchemaId();
    if (schemaId != null) {
      final JsonSchemaObject refSchema = resolveSchemaByReference(service, schemaFile, schemaId);
      if (refSchema == null) return null;
      return findRelativeDefinition(refSchema, splitter, service);
    }
    JsonSchemaObject rootSchema = service.getSchemaObjectForSchemaFile(schemaFile);
    if (rootSchema == null) {
      LOG.debug(String.format("Schema object not found for %s", schemaFile.getPath()));
      return null;
    }
    if (recursive && ref.startsWith("#")) {
      while (rootSchema.isRecursiveAnchor()) {
        JsonSchemaObject backRef = rootSchema.myBackRef;
        if (backRef == null) break;
        VirtualFile file = ObjectUtils.coalesce(backRef.myRawFile, backRef.myFileUrl == null ? null : JsonFileResolver.urlToFile(backRef.myFileUrl));
        if (file == null) break;
        try {
          rootSchema = JsonSchemaReader.readFromFile(service.getProject(), file);
        }
        catch (Exception e) {
          break;
        }
      }
    }
    return findRelativeDefinition(rootSchema, splitter, service);
  }

  private static @Nullable JsonSchemaObject resolveSchemaByReference(@NotNull JsonSchemaService service,
                                                                     @NotNull VirtualFile schemaFile,
                                                                     @NotNull String schemaId) {
    final VirtualFile refFile = service.findSchemaFileByReference(schemaId, schemaFile);
    if (refFile == null) {
      LOG.debug(String.format("Schema file not found by reference: '%s' from %s", schemaId, schemaFile.getPath()));
      return null;
    }
    if (refFile instanceof HttpVirtualFile) {
      RemoteFileInfo info = ((HttpVirtualFile)refFile).getFileInfo();
      if (info != null) {
        RemoteFileState state = info.getState();
        if (state == RemoteFileState.DOWNLOADING_NOT_STARTED) {
          JsonFileResolver.startFetchingHttpFileIfNeeded(refFile, service.getProject());
          return NULL_OBJ;
        }
        else if (state == RemoteFileState.DOWNLOADING_IN_PROGRESS) {
          return NULL_OBJ;
        }
      }
    }
    final JsonSchemaObject refSchema = service.getSchemaObjectForSchemaFile(refFile);
    if (refSchema == null) {
      LOG.debug(String.format("Schema object not found by reference: '%s' from %s", schemaId, schemaFile.getPath()));
      return null;
    }
    return refSchema;
  }

  private static JsonSchemaObject findRelativeDefinition(final @NotNull JsonSchemaObject schema,
                                                         final @NotNull JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter splitter,
                                                         @NotNull JsonSchemaService service) {
    final String path = splitter.getRelativePath();
    if (StringUtil.isEmptyOrSpaces(path)) {
      final String id = splitter.getSchemaId();
      if (isSelfReference(id)) {
        return schema;
      }
      if (id != null && id.startsWith("#") ) {
        final String resolvedId = schema.resolveId(id);
        if (resolvedId == null || id.equals("#" + resolvedId)) return null;
        return findRelativeDefinition(schema, new JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter("#" + resolvedId), service);
      }
      return schema;
    }
    final JsonSchemaObject definition = schema.findRelativeDefinition(path);
    if (definition == null) {
      VirtualFile schemaFile = service.resolveSchemaFile(schema);
      LOG.debug(String.format("Definition not found by reference: '%s' in file %s", path, schemaFile == null ? "(no file)" : schemaFile.getPath()));
    }
    return definition;
  }

  public static @NotNull JsonSchemaObject merge(@NotNull JsonSchemaObject base,
                                                @NotNull JsonSchemaObject other,
                                                @NotNull JsonSchemaObject pointTo) {
    final JsonSchemaObject object = new JsonSchemaObject(pointTo.myRawFile, pointTo.myFileUrl, pointTo.getPointer());
    object.mergeValues(other);
    object.mergeValues(base);
    object.setRef(other.getRef());
    return object;
  }

  private static final class PropertyNamePattern {
    private final @NotNull String myPattern;
    private final @Nullable Pattern myCompiledPattern;
    private final @Nullable String myPatternError;
    private final @NotNull Map<String, Boolean> myValuePatternCache;

    PropertyNamePattern(@NotNull String pattern) {
      myPattern = StringUtil.unescapeBackSlashes(pattern);
      final Pair<Pattern, String> pair = compilePattern(pattern);
      myPatternError = pair.getSecond();
      myCompiledPattern = pair.getFirst();
      myValuePatternCache = CollectionFactory.createConcurrentWeakKeyWeakValueMap();
    }

    public @Nullable String getPatternError() {
      return myPatternError;
    }

    boolean checkByPattern(final @NotNull String name) {
      if (myPatternError != null) return true;
      if (Boolean.TRUE.equals(myValuePatternCache.get(name))) return true;
      assert myCompiledPattern != null;
      boolean matches = matchPattern(myCompiledPattern, name);
      myValuePatternCache.put(name, matches);
      return matches;
    }

    public @NotNull String getPattern() {
      return myPattern;
    }
  }

  private static final class PatternProperties {
    private final @NotNull Map<String, JsonSchemaObject> mySchemasMap;
    private final @NotNull Map<String, Pattern> myCachedPatterns;
    private final @NotNull Map<String, String> myCachedPatternProperties;

    PatternProperties(final @NotNull Map<String, JsonSchemaObject> schemasMap) {
      mySchemasMap = new HashMap<>();
      schemasMap.keySet().forEach(key -> mySchemasMap.put(StringUtil.unescapeBackSlashes(key), schemasMap.get(key)));
      myCachedPatterns = new HashMap<>();
      myCachedPatternProperties = CollectionFactory.createConcurrentWeakKeyWeakValueMap();
      mySchemasMap.keySet().forEach(key -> {
        final Pair<Pattern, String> pair = compilePattern(key);
        if (pair.getSecond() == null) {
          assert pair.getFirst() != null;
          myCachedPatterns.put(key, pair.getFirst());
        }
      });
    }

    public @Nullable JsonSchemaObject getPatternPropertySchema(final @NotNull String name) {
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
  }
}
