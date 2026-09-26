// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.value.RelationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class DfFloatConstantType extends DfConstantType<Float> implements DfFloatType {
  private final boolean shouldWiden;
  
  DfFloatConstantType(float value) {
    this(value, false);
  }

  private DfFloatConstantType(float value, boolean widen) {
    super(value);
    shouldWiden = widen;
  }
  
  public DfFloatConstantType makeWide() {
    return shouldWiden ? this : new DfFloatConstantType(getValue(), true);
  }

  @Override
  public DfType widen() {
    return shouldWiden ? DfTypes.FLOAT : super.widen();
  }

  @Override
  public @NotNull DfType join(@NotNull DfType other) {
    return Objects.requireNonNull(join(other, false));
  }

  @Override
  public @Nullable DfType tryJoinExactly(@NotNull DfType other) {
    return join(other, true);
  }

  private DfType join(@NotNull DfType other, boolean exact) {
    if (other.isSuperType(this)) return other;
    if (other instanceof DfFloatRangeType) {
      return ((DfFloatRangeType)other).join(this, exact);
    }
    if (other instanceof DfFloatConstantType) {
      float val1 = getValue();
      float val2 = ((DfFloatConstantType)other).getValue();
      if (Float.isNaN(val1)) {
        return DfFloatRangeType.create(val2, val2, false, true);
      }
      if (Float.isNaN(val2)) {
        return DfFloatRangeType.create(val1, val1, false, true);
      }
      float from = Math.min(val1, val2);
      float to = Math.max(val1, val2);
      // We don't support disjoint sets, so the best we can do is to return a range from min to max
      return exact ? null : DfFloatRangeType.create(from, to, false, false);
    }
    return exact ? null : DfType.TOP;
  }

  @Override
  public @NotNull DfType fromRelation(@NotNull RelationType relationType) {
    float value = getValue();
    if (Float.isNaN(value)) {
      return relationType == RelationType.NE ? DfTypes.FLOAT : BOTTOM;
    }
    return DfFloatRangeType.fromRelation(relationType, value, value);
  }

  @Override
  public @NotNull DfType tryNegate() {
    float value = getValue();
    if (Float.isNaN(value)) {
      return DfFloatRangeType.create(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, false, false);
    }
    return DfFloatRangeType.create(value, value, true, true);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    return o instanceof DfFloatConstantType that && super.equals(o) && shouldWiden == that.shouldWiden;
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + (shouldWiden ? 1 : 0);
  }
}
