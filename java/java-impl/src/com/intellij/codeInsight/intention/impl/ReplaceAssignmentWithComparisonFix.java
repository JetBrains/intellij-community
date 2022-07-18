// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceAssignmentWithComparisonFix extends LocalQuickFixAndIntentionActionOnPsiElement
  implements IntentionActionWithFixAllOption {
  public ReplaceAssignmentWithComparisonFix(@NotNull PsiAssignmentExpression expr) {
    super(expr);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiBinaryExpression
      comparisonExpr = (PsiBinaryExpression)JavaPsiFacade.getElementFactory(project).createExpressionFromText("a==b", startElement);
    PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)startElement;
    comparisonExpr.getLOperand().replace(assignmentExpression.getLExpression());
    PsiExpression rOperand = comparisonExpr.getROperand();
    assert rOperand != null;
    PsiExpression rExpression = assignmentExpression.getRExpression();
    assert rExpression != null;
    rOperand.replace(rExpression);
    CodeStyleManager.getInstance(project).reformat(assignmentExpression.replace(comparisonExpr));
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return CommonQuickFixBundle.message("fix.replace.x.with.y", "=", "==");
  }
}
