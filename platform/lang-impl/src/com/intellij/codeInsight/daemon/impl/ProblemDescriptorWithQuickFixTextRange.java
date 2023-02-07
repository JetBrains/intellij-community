// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

public class ProblemDescriptorWithQuickFixTextRange extends ProblemDescriptorBase {
  private final TextRange myQuickFixTextRange;

  public ProblemDescriptorWithQuickFixTextRange(@NotNull ProblemDescriptorBase pd,
                                                @NotNull TextRange quickFixTextRange) {
    super(pd.getStartElement(), pd.getEndElement(), pd.getDescriptionTemplate(), pd.getFixes(),
          pd.getHighlightType(), pd.isAfterEndOfLine(), pd.getTextRangeInElement(), pd.showTooltip(), pd.isOnTheFly());
    myQuickFixTextRange = quickFixTextRange;
  }

  public TextRange getQuickFixTextRange() {
    return myQuickFixTextRange;
  }
}
