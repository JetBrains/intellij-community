// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.psi.PsiPrimitiveType;
import org.jetbrains.annotations.NotNull;

class DfLongConstantType extends DfConstantType<Long> implements DfLongType {
  DfLongConstantType(long value) {
    super(value);
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
}
