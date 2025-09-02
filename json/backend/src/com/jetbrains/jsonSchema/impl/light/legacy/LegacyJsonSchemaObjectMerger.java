// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.legacy;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaObjectImpl;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import com.jetbrains.jsonSchema.impl.PatternProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Deprecated
public class LegacyJsonSchemaObjectMerger implements JsonSchemaObjectMerger {
  private static @NotNull JsonSchemaObjectImpl mergeObjectsInner(@NotNull JsonSchemaObjectImpl first,
                                                                 @NotNull JsonSchemaObjectImpl second,
                                                                 @NotNull JsonSchemaObjectImpl pointTo) {
    final JsonSchemaObjectImpl object = new JsonSchemaObjectImpl(pointTo.getRawFile(), pointTo.getFileUrl(), pointTo.getPointer());
    mergeValues(object, second);
    mergeValues(object, first);
    object.setRef(second.getRef());
    return object;
  }


  private static void mergeValues(@NotNull JsonSchemaObjectImpl base, @NotNull JsonSchemaObjectImpl other) {
    // we do not copy id, schema
    base.myProperties = mergeProperties(base.getProperties(), other.getProperties());
    base.myDefinitionsMap = mergeMaps(base.getDefinitionsMap(), other.getDefinitionsMap());

    var baseProperties = base.getPatternProperties() == null ? null : base.getPatternProperties().getSchemasMap();
    var otherProperties = other.getPatternProperties() == null ? null : other.getPatternProperties().getSchemasMap();
    var patternPropertiesOrNull = mergeMaps(baseProperties, otherProperties);
    base.myPatternProperties = patternPropertiesOrNull == null ? null : new PatternProperties(patternPropertiesOrNull);

    if (!StringUtil.isEmptyOrSpaces(other.getTitle())) base.setTitle(other.getTitle());
    if (!StringUtil.isEmptyOrSpaces(other.getDescription())) base.setDescription(other.getDescription());
    if (!StringUtil.isEmptyOrSpaces(other.getHtmlDescription())) base.setHtmlDescription(other.getHtmlDescription());
    if (!StringUtil.isEmptyOrSpaces(other.getDeprecationMessage())) base.setDeprecationMessage(other.getDeprecationMessage());

    base.setExclusiveMaximum(base.isExclusiveMaximum() || other.isExclusiveMaximum());
    base.setExclusiveMinimum(base.isExclusiveMinimum() || other.isExclusiveMinimum());

    if (other.myUniqueItems != null) base.setUniqueItems(other.myUniqueItems);

    base.setShouldValidateAgainstJSType(base.isShouldValidateAgainstJSType() || other.isShouldValidateAgainstJSType());

    base.setForceCaseInsensitive(base.isForceCaseInsensitive() || other.isForceCaseInsensitive());

    var mergedRequired = mergeSets(base.getRequired(), other.getRequired());
    base.setRequired(mergedRequired);

    base.setPropertyDependencies(mergeMaps(base.getPropertyDependencies(), other.getPropertyDependencies()));
    base.setSchemaDependencies(mergeMaps(base.getSchemaDependencies(), other.getSchemaDependencies()));
    base.setEnumMetadata(mergeMaps(base.getEnumMetadata(), other.getEnumMetadata()));
    base.setAllOf(mergeLists(base.getAllOf(), other.getAllOf()));
    base.setAnyOf(mergeLists(base.getAnyOf(), other.getAnyOf()));
    base.setOneOf(mergeLists(base.getOneOf(), other.getOneOf()));
    base.setIfThenElse(mergeLists(base.getIfThenElse(), other.getIfThenElse()));

    if (other.myDefault != null) base.setDefault(other.myDefault);
    if (other.getExample() != null) base.setExample(other.getExample());
    if (other.getFormat() != null) base.setFormat(other.getFormat());
    if (other.getMultipleOf() != null) base.setMultipleOf(other.getMultipleOf());
    if (other.getMaximum() != null) base.setMaximum(other.getMaximum());
    if (other.getMinimum() != null) base.setMinimum(other.getMinimum());
    if (other.getExclusiveMaximumNumber() != null) base.setExclusiveMaximumNumber(other.getExclusiveMaximumNumber());
    if (other.getExclusiveMinimumNumber() != null) base.setExclusiveMinimumNumber(other.getExclusiveMinimumNumber());
    if (other.getMaxLength() != null) base.setMaxLength(other.getMaxLength());
    if (other.getMinLength() != null) base.setMinLength(other.getMinLength());
    if (other.myPattern != null) base.myPattern = other.myPattern;
    if (other.getAdditionalPropertiesSchema() != null) base.setAdditionalPropertiesSchema(other.getAdditionalPropertiesSchema());
    if (other.getPropertyNamesSchema() != null) base.setPropertyNamesSchema(other.getPropertyNamesSchema());
    if (other.myAdditionalItemsAllowed != null) base.setAdditionalItemsAllowed(other.myAdditionalItemsAllowed);
    if (other.getAdditionalItemsSchema() != null) base.setAdditionalItemsSchema(other.getAdditionalItemsSchema());
    if (other.getItemsSchema() != null) base.setItemsSchema(other.getItemsSchema());
    if (other.getContainsSchema() != null) base.setContainsSchema(other.getContainsSchema());
    if (other.getItemsSchemaList() != null) base.setItemsSchemaList(mergeLists(base.getItemsSchemaList(), other.getItemsSchemaList()));
    if (other.getMaxItems() != null) base.setMaxItems(other.getMaxItems());
    if (other.getMinItems() != null) base.setMinItems(other.getMinItems());
    if (other.getMaxProperties() != null) base.setMaxProperties(other.getMaxProperties());
    if (other.getMinProperties() != null) base.setMinProperties(other.getMinProperties());
    if (other.getEnum() != null) base.setEnum(other.getEnum());
    if (other.getNot() != null) base.setNot(other.getNot());
    if (other.getLanguageInjection() != null) base.setLanguageInjection(other.getLanguageInjection());
    if (other.getMetadata() != null) base.setMetadata(other.getMetadata());

    //computed together because influence each other
    var mergedExclusionAndType = computeMergedExclusionAndType(base.getType(), other.getType(), other.getTypeVariants());
    if (mergedExclusionAndType != null) base.setType(mergedExclusionAndType.type);
    if (mergedExclusionAndType != null && mergedExclusionAndType.isValidByExclusion != null) {
      base.setValidByExclusion(mergedExclusionAndType.isValidByExclusion);
    }

    var exclusionAndTypesInfo = mergeTypeVariantSets(base.getTypeVariants(), other.getTypeVariants());
    if (exclusionAndTypesInfo.types != null) base.setTypeVariants(exclusionAndTypesInfo.types);
    if (exclusionAndTypesInfo.isValidByExclusion != null) base.setValidByExclusion(exclusionAndTypesInfo.isValidByExclusion);

    if (other.myAdditionalPropertiesAllowed != null) {
      base.setAdditionalPropertiesAllowed(other.myAdditionalPropertiesAllowed);
      if (other.myAdditionalPropertiesAllowed == Boolean.FALSE) {
        base.addAdditionalPropsNotAllowedFor(other.getFileUrl(), other.getPointer());
      }
    }
  }

  @Override
  public @NotNull JsonSchemaObject mergeObjects(@NotNull JsonSchemaObject base, @NotNull JsonSchemaObject other, @NotNull JsonSchemaObject pointTo) {
    JsonSchemaObjectImpl base1 = (JsonSchemaObjectImpl)base;
    JsonSchemaObjectImpl other1 = (JsonSchemaObjectImpl)other;
    JsonSchemaObjectImpl pointTo1 = (JsonSchemaObjectImpl)pointTo;
    return mergeObjectsInner(base1, other1, pointTo1);
  }

  public static ExclusionAndTypesInfo mergeTypeVariantSets(@Nullable Set<JsonSchemaType> self, @Nullable Set<JsonSchemaType> other) {
    Boolean exclusionType = null;
    if (self == null) return new ExclusionAndTypesInfo(exclusionType, other);
    if (other == null) return new ExclusionAndTypesInfo(exclusionType, self);

    Set<JsonSchemaType> resultSet = EnumSet.noneOf(JsonSchemaType.class);
    for (JsonSchemaType type : self) {
      ProgressManager.checkCanceled();
      var mergedExclusionAndType = computeMergedExclusionAndType(type, null, other);
      if (mergedExclusionAndType != null && mergedExclusionAndType.type != null) resultSet.add(mergedExclusionAndType.type);
      exclusionType = mergedExclusionAndType != null ? mergedExclusionAndType.isValidByExclusion : exclusionType;
    }

    if (resultSet.isEmpty()) {
      return new ExclusionAndTypesInfo(false, other);
    }

    return new ExclusionAndTypesInfo(exclusionType, resultSet);
  }

  public static @Nullable LegacyJsonSchemaObjectMerger.ExclusionAndTypeInfo computeMergedExclusionAndType(@Nullable JsonSchemaType selfType,
                                                                                                          @Nullable JsonSchemaType otherType,
                                                                                                          @Nullable Set<JsonSchemaType> otherTypeVariants) {
    if (selfType == null) return new ExclusionAndTypeInfo(null, otherType);
    if (otherType == null) {
      if (otherTypeVariants != null && !otherTypeVariants.isEmpty()) {
        Set<JsonSchemaType> filteredVariants = EnumSet.noneOf(JsonSchemaType.class);
        for (JsonSchemaType variant : otherTypeVariants) {
          ProgressManager.checkCanceled();
          JsonSchemaType subtype = getSubtypeOfBoth(selfType, variant);
          if (subtype != null) filteredVariants.add(subtype);
        }
        if (filteredVariants.isEmpty()) {
          return new ExclusionAndTypeInfo(false, selfType);
        }
        if (filteredVariants.size() == 1) {
          return new ExclusionAndTypeInfo(null, filteredVariants.iterator().next());
        }
        return null; // will be handled by variants
      }
      return new ExclusionAndTypeInfo(null, selfType);
    }

    JsonSchemaType subtypeOfBoth = getSubtypeOfBoth(selfType, otherType);
    if (subtypeOfBoth == null) {
      return new ExclusionAndTypeInfo(false, otherType);
    }
    return new ExclusionAndTypeInfo(null, subtypeOfBoth);
  }

  public static class ExclusionAndTypeInfo {
    public ExclusionAndTypeInfo(Boolean isValidByExclusion, JsonSchemaType type) {
      this.isValidByExclusion = isValidByExclusion;
      this.type = type;
    }

    public Boolean isValidByExclusion;
    public JsonSchemaType type;
  }

  public static class ExclusionAndTypesInfo {
    public ExclusionAndTypesInfo(Boolean isValidByExclusion, Set<JsonSchemaType> types) {
      this.isValidByExclusion = isValidByExclusion;
      this.types = types;
    }

    public Boolean isValidByExclusion;
    public Set<JsonSchemaType> types;
  }

  public static @Nullable JsonSchemaType getSubtypeOfBoth(@NotNull JsonSchemaType selfType,
                                                          @NotNull JsonSchemaType otherType) {
    return JsonSchemaObjectImpl.getSubtypeOfBoth(selfType, otherType);
  }

  public static HashMap<String, JsonSchemaObjectImpl> mergeProperties(@NotNull Map<String, JsonSchemaObjectImpl> baseProperties,
                                                                      @NotNull Map<String, JsonSchemaObjectImpl> otherProperties) {
    var mergedProperties = new HashMap<>(baseProperties);
    for (var prop : otherProperties.entrySet()) {
      String key = prop.getKey();
      var otherProp = prop.getValue();

      if (!baseProperties.containsKey(key)) {
        mergedProperties.put(key, otherProp);
      }
      else {
        JsonSchemaObjectImpl existingProp = baseProperties.get(key);
        mergedProperties.put(key, mergeObjectsInner(existingProp, otherProp, otherProp));
      }
    }

    return mergedProperties;
  }

  public static @Nullable <T> List<T> mergeLists(@Nullable List<T> first, @Nullable List<T> second) {
    if (first == null || first.isEmpty()) return second;
    if (second == null) {
      return first;
    }
    var target = new ArrayList<T>(first.size() + second.size());
    target.addAll(first);
    target.addAll(second);
    return target;
  }

  public static @Nullable <T> Set<T> mergeSets(@Nullable Set<T> first, @Nullable Set<T> second) {
    if (first == null || first.isEmpty()) return second;
    if (second == null) {
      return first;
    }
    var target = new HashSet<T>(first.size() + second.size());
    target.addAll(first);
    target.addAll(second);
    return target;
  }

  public static @Nullable <K, V> Map<K, V> mergeMaps(@Nullable Map<K, V> first, @Nullable Map<K, V> second) {
    if (first == null || first.isEmpty()) return second;
    if (second == null) {
      return first;
    }
    var merged = new HashMap<K, V>(mapSize(second) + mapSize(first));
    merged.putAll(first);
    merged.putAll(second);
    return merged;
  }

  public static <K, V> int mapSize(@Nullable Map<K, V> map) {
    if (map == null || map.isEmpty()) return 0;
    return map.size();
  }
}
