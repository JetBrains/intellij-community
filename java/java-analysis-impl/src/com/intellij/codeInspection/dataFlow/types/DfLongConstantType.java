// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class DfLongConstantType extends DfConstantType<Long> implements DfLongType {
  private final @Nullable LongRangeSet myWideRange;

  DfLongConstantType(long value, @Nullable LongRangeSet wideRange) {
    super(value);
    myWideRange = wideRange;
  }

  @Override
  public @NotNull LongRangeSet getWideRange() {
    return myWideRange == null ? getRange() : myWideRange;
  }

  @Override
  public boolean isSuperType(@NotNull DfType other) {
    return DfLongType.super.isSuperType(other);
  }

  @Override
  public @NotNull DfType meet(@NotNull DfType other) {
    return DfLongType.super.meet(other);
  }

  @Override
  public @NotNull LongRangeSet getRange() {
    return LongRangeSet.point(getValue());
  }

  @Override
  public @NotNull DfType fromRelation(@NotNull RelationType relationType) {
    return DfLongType.super.fromRelation(relationType);
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj || super.equals(obj) && Objects.equals(((DfLongConstantType)obj).myWideRange, myWideRange);
  }

  @Override
  public @NotNull String toString() {
    return getValue() + "L";
  }
}
