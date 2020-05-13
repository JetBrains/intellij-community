// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DfIntType extends DfIntegralType {
  @Override
  @NotNull
  LongRangeSet getRange();
  
  @NotNull
  @Override
  default DfType join(@NotNull DfType other) {
    if (!(other instanceof DfIntType)) return DfTypes.TOP;
    return DfTypes.intRange(((DfIntType)other).getRange().unite(getRange()));
  }

  @NotNull
  @Override
  default DfType meet(@NotNull DfType other) {
    if (other == DfTypes.TOP) return this;
    if (!(other instanceof DfIntType)) return DfTypes.BOTTOM;
    return DfTypes.intRange(((DfIntType)other).getRange().intersect(getRange()));
  }
  
  @NotNull
  @Override
  default DfType meetRange(@NotNull LongRangeSet range) {
    return meet(DfTypes.intRangeClamped(range));
  }

  @NotNull
  @Override
  default PsiPrimitiveType getPsiType() {
    return PsiType.INT;
  }

  @Nullable
  @Override
  default DfType tryNegate() {
    LongRangeSet range = getRange();
    LongRangeSet res = DfIntRangeType.FULL_RANGE.subtract(range);
    return res.intersects(range) ? null : DfTypes.intRange(res);
  }

  @NotNull
  static LongRangeSet extractRange(@NotNull DfType type) {
    return type instanceof DfIntegralType ? ((DfIntegralType)type).getRange().intersect(DfIntRangeType.FULL_RANGE) :
           DfIntRangeType.FULL_RANGE;
  }
}
