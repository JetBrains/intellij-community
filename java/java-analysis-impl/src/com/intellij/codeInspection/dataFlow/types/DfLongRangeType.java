// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

class DfLongRangeType implements DfLongType {
  private final LongRangeSet myRange;

  DfLongRangeType(LongRangeSet range) {
    myRange = range;
  }

  @NotNull
  @Override
  public LongRangeSet getRange() {
    return myRange;
  }

  @Override
  public boolean isSuperType(@NotNull DfType other) {
    if (other == DfTypes.BOTTOM) return true;
    if (!(other instanceof DfLongType)) return false;
    return myRange.contains(((DfLongType)other).getRange());
  }

  @Override
  public int hashCode() {
    return myRange.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this || obj instanceof DfLongRangeType && ((DfLongRangeType)obj).myRange.equals(myRange);
  }

  @Override
  public String toString() {
    if (myRange == LongRangeSet.all()) return "long";
    return "long " + myRange.getPresentationText(PsiType.LONG);
  }
}
