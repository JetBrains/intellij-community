// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DfLongRangeType extends DfAbstractRangeType implements DfLongType {
  DfLongRangeType(@NotNull LongRangeSet range, @Nullable LongRangeSet wideRange) {
    super(range, wideRange);
  }

  @Override
  public String toString() {
    if (getRange() == LongRangeSet.all()) return PsiKeyword.LONG;
    return PsiKeyword.LONG + " " + getRange().getPresentationText(PsiType.LONG);
  }
}
