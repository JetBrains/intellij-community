// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.psi.PsiPrimitiveType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

class DfLongConstantType extends DfConstantType<Long> implements DfLongType {
  private final @Nullable LongRangeSet myWideRange;
  
  DfLongConstantType(long value, @Nullable LongRangeSet wideRange) {
    super(value);
    myWideRange = wideRange;
  }

  @NotNull
  @Override
  public LongRangeSet getWideRange() {
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

  @NotNull
  @Override
  public PsiPrimitiveType getPsiType() {
    return DfLongType.super.getPsiType();
  }

  @NotNull
  @Override
  public LongRangeSet getRange() {
    return LongRangeSet.point(getValue());
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj || super.equals(obj) && Objects.equals(((DfLongConstantType)obj).myWideRange, myWideRange);
  }
}
