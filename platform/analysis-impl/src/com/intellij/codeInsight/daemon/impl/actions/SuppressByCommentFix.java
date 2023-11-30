// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuppressByCommentFix extends AbstractBatchSuppressByNoInspectionCommentFix {
  protected Class<? extends PsiElement> mySuppressionHolderClass;

  public SuppressByCommentFix(@NotNull HighlightDisplayKey key, @NotNull Class<? extends PsiElement> suppressionHolderClass) {
    this(key.getID(), suppressionHolderClass);
  }

  public SuppressByCommentFix(@NotNull String toolId, @NotNull Class<? extends PsiElement> suppressionHolderClass) {
    this(toolId);
    mySuppressionHolderClass = suppressionHolderClass;
  }

  private SuppressByCommentFix(String ID) {
    super(ID, false);
  }

  @Override
  public @IntentionName @NotNull String getText() {
    return AnalysisBundle.message("suppress.inspection.statement");
  }

  @Override
  public @Nullable PsiElement getContainer(PsiElement context) {
    return PsiTreeUtil.getParentOfType(context, mySuppressionHolderClass);
  }
}
