// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.types;

import com.intellij.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.psi.PsiTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DfLongRangeType extends DfAbstractRangeType implements DfLongType {
  DfLongRangeType(@NotNull LongRangeSet range, @Nullable LongRangeSet wideRange) {
    super(range, wideRange);
  }

  @Override
  public @NotNull String toString() {
    if (getRange() == LongRangeSet.all()) return JavaKeywords.LONG;
    return JavaKeywords.LONG + " " + JvmPsiRangeSetUtil.getPresentationText(getRange(), PsiTypes.longType());
  }
}
