// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.PsiUpdateModCommandAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiExpression;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceExpressionAction extends PsiUpdateModCommandAction<PsiExpression> {
  private final String myReplacement;
  private final String myPresentation;
  private final String myOrigText;

  public ReplaceExpressionAction(@NotNull PsiExpression expression, @NotNull String replacement, @NotNull String presentation) {
    super(expression);
    myOrigText = expression.getText();
    myReplacement = replacement;
    myPresentation = presentation;
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiExpression element, @NotNull ModPsiUpdater updater) {
    new CommentTracker().replaceAndRestoreComments(element, myReplacement);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpression element) {
    return Presentation.of(CommonQuickFixBundle.message("fix.replace.x.with.y", myOrigText, myPresentation));
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("intention.family.name.replace.with.expression");
  }
}
