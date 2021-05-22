// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

final class DfIntRangeType extends DfAbstractRangeType implements DfIntType {
  static final @NotNull LongRangeSet FULL_RANGE = Objects.requireNonNull(LongRangeSet.fromType(PsiType.INT));

  DfIntRangeType(@NotNull LongRangeSet range, @Nullable LongRangeSet wideRange) {
    super(checkRange(range), wideRange);
  }

  private static @NotNull LongRangeSet checkRange(@NotNull LongRangeSet range) {
    if (!FULL_RANGE.contains(range)) {
      throw new IllegalArgumentException("Illegal range supplied for int type: " + range);
    }
    return FULL_RANGE.equals(range) ? FULL_RANGE : range;
  }

  @Override
  public String toString() {
    if (getRange() == FULL_RANGE) return PsiKeyword.INT;
    return PsiKeyword.INT + " " + getRange().getPresentationText(PsiType.INT);
  }
}
