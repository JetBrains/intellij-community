// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.EditorUpdater;
import com.intellij.codeInspection.PsiUpdateModCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiKeyword;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConvertExpressionToReturnFix extends PsiUpdateModCommandAction<PsiExpression> {
  public ConvertExpressionToReturnFix(@NotNull PsiExpression expression) {
    super(expression);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpression element) {
    return Presentation.of(getFamilyName())
      .withPriority(PriorityAction.Priority.HIGH)
      .withFixAllOption(this);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiExpression expression, @NotNull EditorUpdater updater) {
    CommentTracker tracker = new CommentTracker();
    tracker.replaceAndRestoreComments(expression.getParent(), "return " + tracker.text(expression) + ";");
  }

  @Override
  public @NotNull String getFamilyName() {
    return CommonQuickFixBundle.message("fix.insert.x", PsiKeyword.RETURN);
  }
}
