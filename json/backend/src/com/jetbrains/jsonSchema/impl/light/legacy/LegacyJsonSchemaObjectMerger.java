// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.legacy;

import com.intellij.openapi.progress.ProgressManager;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

@ApiStatus.Internal
public final class LegacyJsonSchemaObjectMerger {
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
}
