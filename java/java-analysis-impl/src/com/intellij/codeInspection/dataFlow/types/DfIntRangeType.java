// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

class DfIntRangeType implements DfIntType {
  static final @NotNull LongRangeSet FULL_RANGE = Objects.requireNonNull(LongRangeSet.fromType(PsiType.INT));
  private final LongRangeSet myRange;

  DfIntRangeType(LongRangeSet range) {
    if (!FULL_RANGE.contains(range)) {
      throw new IllegalArgumentException("Illegal range supplied for int type: " + range);
    }
    myRange = FULL_RANGE.equals(range) ? FULL_RANGE : range;
  }

  @NotNull
  @Override
  public LongRangeSet getRange() {
    return myRange;
  }

  @Override
  public boolean isSuperType(@NotNull DfType other) {
    if (other == DfTypes.BOTTOM) return true;
    if (!(other instanceof DfIntType)) return false;
    return myRange.contains(((DfIntType)other).getRange());
  }

  @Override
  public int hashCode() {
    return myRange.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this || obj instanceof DfIntRangeType && ((DfIntRangeType)obj).myRange.equals(myRange);
  }

  @Override
  public String toString() {
    if (myRange == FULL_RANGE) return "int";
    return "int " + myRange.getPresentationText(PsiType.INT);
  }
}
