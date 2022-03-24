// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class DfIntConstantType extends DfConstantType<Integer> implements DfIntType {
  private final @Nullable LongRangeSet myWideRange;

  DfIntConstantType(int value, @Nullable LongRangeSet wideRange) {
    super(value);
    myWideRange = wideRange;
  }

  @Override
  public boolean isSuperType(@NotNull DfType other) {
    return DfIntType.super.isSuperType(other);
  }

  @Override
  public @NotNull DfType meet(@NotNull DfType other) {
    return DfIntType.super.meet(other);
  }

  @NotNull
  @Override
  public LongRangeSet getWideRange() {
    return myWideRange == null ? getRange() : myWideRange;
  }

  @NotNull
  @Override
  public LongRangeSet getRange() {
    return LongRangeSet.point(getValue());
  }

  @Override
  public @NotNull DfType fromRelation(@NotNull RelationType relationType) {
    return DfIntType.super.fromRelation(relationType);
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj || super.equals(obj) && Objects.equals(((DfIntConstantType)obj).myWideRange, myWideRange);
  }
}
