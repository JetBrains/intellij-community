// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.types.DfPrimitiveType;
import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.java.analysis.JavaAnalysisBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

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
  NULL("Null", JavaAnalysisBundle.messagePointer("nullability.null"), Nullability.NULLABLE),
  NULLABLE("Nullable", JavaAnalysisBundle.messagePointer("nullability.nullable"), Nullability.NULLABLE),
  NOT_NULL("Not-null", JavaAnalysisBundle.messagePointer("nullability.non.null"), Nullability.NOT_NULL),
  UNKNOWN("Unknown", () -> "", Nullability.UNKNOWN),
  /**
   * Means: non-stable variable declared as Nullable was checked for nullity and flushed afterwards (e.g. by unknown method call),
   * so we are unsure about its nullability anymore.
   */
  FLUSHED("Flushed", () -> "", Nullability.UNKNOWN);

  private final @NotNull String myInternalName;
  private final @NotNull Supplier<@Nls String> myPresentationalName;
  private final @NotNull Nullability myNullability;

  DfaNullability(@NotNull String internalName, @NotNull Supplier<@Nls String> presentationalName, @NotNull Nullability nullability) {
    myInternalName = internalName;
    myPresentationalName = presentationalName;
    myNullability = nullability;
  }

  public @NotNull String getInternalName() {
    return myInternalName;
  }

  public @NotNull @Nls String getPresentationName() {
    return myPresentationalName.get();
  }

  public @NotNull DfaNullability unite(@NotNull DfaNullability other) {
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

  public @Nullable DfaNullability intersect(@NotNull DfaNullability right) {
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

  public static @NotNull Nullability toNullability(@Nullable DfaNullability dfaNullability) {
    return dfaNullability == null ? Nullability.UNKNOWN : dfaNullability.myNullability;
  }

  public static @NotNull DfaNullability fromNullability(@NotNull Nullability nullability) {
    return switch (nullability) {
      case NOT_NULL -> NOT_NULL;
      case NULLABLE -> NULLABLE;
      case UNKNOWN -> UNKNOWN;
    };
  }

  public @NotNull DfReferenceType asDfType() {
    return switch (this) {
      case NULL -> DfTypes.NULL;
      case NOT_NULL -> DfTypes.NOT_NULL_OBJECT;
      case UNKNOWN -> DfTypes.OBJECT_OR_NULL;
      default -> DfTypes.customObject(TypeConstraints.TOP, this, Mutability.UNKNOWN, null, DfType.BOTTOM);
    };
  }

  public static @NotNull DfaNullability fromDfType(@NotNull DfType type) {
    return type == DfType.FAIL || type instanceof DfPrimitiveType ? NOT_NULL : 
           type instanceof DfReferenceType ? ((DfReferenceType)type).getNullability() : UNKNOWN;
  }
}
