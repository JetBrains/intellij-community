// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DfLongType extends DfIntegralType {
  @Override
  @NotNull
  LongRangeSet getRange();

  @NotNull
  @Override
  default DfType join(@NotNull DfType other) {
    if (!(other instanceof DfLongType)) return DfTypes.TOP;
    return DfTypes.longRange(((DfLongType)other).getRange().unite(getRange()));
  }

  @NotNull
  @Override
  default DfType meet(@NotNull DfType other) {
    if (other == DfTypes.TOP) return this;
    if (!(other instanceof DfLongType)) return DfTypes.BOTTOM;
    return DfTypes.longRange(((DfLongType)other).getRange().intersect(getRange()));
  }

  @NotNull
  @Override
  default DfType meetRange(@NotNull LongRangeSet range) {
    return meet(DfTypes.longRange(range));
  }

  @NotNull
  @Override
  default PsiPrimitiveType getPsiType() {
    return PsiType.LONG;
  }

  @Override
  @Nullable
  default DfType tryNegate() {
    LongRangeSet range = getRange();
    LongRangeSet res = LongRangeSet.all().subtract(range);
    return res.intersects(range) ? null : DfTypes.longRange(res);
  }

  @NotNull
  static LongRangeSet extractRange(@NotNull DfType type) {
    return type instanceof DfIntegralType ? ((DfIntegralType)type).getRange() : LongRangeSet.all();
  }
}
