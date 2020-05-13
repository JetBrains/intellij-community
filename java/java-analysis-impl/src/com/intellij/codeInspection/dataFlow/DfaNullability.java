// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a value nullability within DFA. Unlike {@link Nullability} may have more fine-grained
 * values useful during the DFA. If you have a DfaNullability value (e.g. from {@link CommonDataflow}),
 * and want to check if it's nullable, or not, it's advised to convert it to {@link Nullability} first,
 * as more values could be introduced to this enum in future.
 */
public enum DfaNullability {
  /**
   * Means: exactly null
   */
  NULL("Null", "null", Nullability.NULLABLE),
  NULLABLE("Nullable", "nullable", Nullability.NULLABLE),
  NOT_NULL("Not-null", "non-null", Nullability.NOT_NULL),
  UNKNOWN("Unknown", "", Nullability.UNKNOWN),
  /**
   * Means: non-stable variable declared as Nullable was checked for nullity and flushed afterwards (e.g. by unknown method call),
   * so we are unsure about its nullability anymore.
   */
  FLUSHED("Flushed", "", Nullability.UNKNOWN);

  private final @NotNull String myInternalName;
  private final @NotNull String myPresentationalName;
  private final @NotNull Nullability myNullability;

  DfaNullability(@NotNull String internalName, @NotNull String presentationalName, @NotNull Nullability nullability) {
    myInternalName = internalName;
    myPresentationalName = presentationalName;
    myNullability = nullability;
  }

  @NotNull
  public String getInternalName() {
    return myInternalName;
  }

  @NotNull
  public String getPresentationName() {
    return myPresentationalName;
  }

  @NotNull
  public DfaNullability unite(@NotNull DfaNullability other) {
    if (this == other) {
      return this;
    }
    if (this == NULL || other == NULL ||
        this == NULLABLE || other == NULLABLE) {
      return NULLABLE;
    }
    if (this == FLUSHED || other == FLUSHED) {
      return FLUSHED;
    }
    return UNKNOWN;
  }

  @Nullable
  public DfaNullability intersect(@NotNull DfaNullability right) {
    if (this == NOT_NULL) {
      return right == NULL ? null : NOT_NULL;
    }
    if (right == NOT_NULL) {
      return this == NULL ? null : NOT_NULL;
    }
    if (this == UNKNOWN) return right;
    if (right == UNKNOWN) return this;
    if (this == FLUSHED && toNullability(right) == Nullability.NULLABLE ||
        right == FLUSHED && toNullability(this) == Nullability.NULLABLE) {
      return NULLABLE;
    }
    return equals(right) ? this : null;
  }

  @NotNull
  public static Nullability toNullability(@Nullable DfaNullability dfaNullability) {
    return dfaNullability == null ? Nullability.UNKNOWN : dfaNullability.myNullability;
  }

  @NotNull
  public static DfaNullability fromNullability(@NotNull Nullability nullability) {
    switch (nullability) {
      case NOT_NULL:
        return NOT_NULL;
      case NULLABLE:
        return NULLABLE;
      case UNKNOWN:
        return UNKNOWN;
    }
    throw new IllegalStateException("Unknown nullability: "+nullability);
  }

  @NotNull
  public DfReferenceType asDfType() {
    switch (this) {
      case NULL:
        return DfTypes.NULL;
      case NOT_NULL:
        return DfTypes.NOT_NULL_OBJECT;
      case UNKNOWN:
        return DfTypes.OBJECT_OR_NULL;
      default:
        return DfTypes.customObject(TypeConstraints.TOP, this, Mutability.UNKNOWN, null, DfTypes.BOTTOM);
    }
  }

  @NotNull
  public static DfaNullability fromDfType(@NotNull DfType type) {
    return type instanceof DfReferenceType ? ((DfReferenceType)type).getNullability() : UNKNOWN;
  }
}
