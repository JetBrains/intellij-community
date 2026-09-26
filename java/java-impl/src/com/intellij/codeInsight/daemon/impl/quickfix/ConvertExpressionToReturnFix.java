// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiStatement;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConvertExpressionToReturnFix extends PsiUpdateModCommandAction<PsiExpression> {
  private static final Logger LOG = Logger.getInstance(ConvertExpressionToReturnFix.class);
  
  public ConvertExpressionToReturnFix(@NotNull PsiExpression expression) {
    super(expression);
    PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiStatement)) {
      LOG.error("Parent is not a statement but " + parent.getClass());
    }
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpression element) {
    return Presentation.of(getFamilyName())
      .withPriority(PriorityAction.Priority.HIGH)
      .withFixAllOption(this);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiExpression expression, @NotNull ModPsiUpdater updater) {
    CommentTracker tracker = new CommentTracker();
    tracker.replaceAndRestoreComments(expression.getParent(), "return " + tracker.text(expression) + ";");
  }

  @Override
  public @NotNull String getFamilyName() {
    return CommonQuickFixBundle.message("fix.insert.x", JavaKeywords.RETURN);
  }
}
