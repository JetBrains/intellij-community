// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceAssignmentWithComparisonFix extends PsiUpdateModCommandAction<PsiAssignmentExpression> {
  public ReplaceAssignmentWithComparisonFix(@NotNull PsiAssignmentExpression expr) {
    super(expr);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiAssignmentExpression assignmentExpression, @NotNull ModPsiUpdater updater) {
    PsiExpression rExpression = assignmentExpression.getRExpression();
    if (rExpression == null) return;
    Project project = context.project();
    PsiBinaryExpression
      comparisonExpr = (PsiBinaryExpression)JavaPsiFacade.getElementFactory(project).createExpressionFromText("a==b", assignmentExpression);
    comparisonExpr.getLOperand().replace(assignmentExpression.getLExpression());
    PsiExpression rOperand = comparisonExpr.getROperand();
    assert rOperand != null;
    rOperand.replace(rExpression);
    CodeStyleManager.getInstance(project).reformat(assignmentExpression.replace(comparisonExpr));
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiAssignmentExpression element) {
    return Presentation.of(getFamilyName()).withFixAllOption(this);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return CommonQuickFixBundle.message("fix.replace.x.with.y", "=", "==");
  }
}
