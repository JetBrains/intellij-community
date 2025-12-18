// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.legacy;

import com.intellij.openapi.progress.ProgressManager;
import com.jetbrains.jsonSchema.impl.JsonSchemaObjectImpl;
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
    return JsonSchemaObjectImpl.getSubtypeOfBoth(selfType, otherType);
  }
}
