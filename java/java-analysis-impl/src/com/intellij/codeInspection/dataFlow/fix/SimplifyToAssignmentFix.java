// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.fix;

import com.intellij.codeInspection.PsiUpdateModCommandQuickFix;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class SimplifyToAssignmentFix extends PsiUpdateModCommandQuickFix {
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaAnalysisBundle.message("inspection.data.flow.simplify.to.assignment.quickfix.name");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement psiElement, @NotNull ModPsiUpdater updater) {
    final PsiAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(psiElement, PsiAssignmentExpression.class);
    if (assignmentExpression == null) {
      return;
    }

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final String lExpressionText = assignmentExpression.getLExpression().getText();
    final PsiExpression rExpression = assignmentExpression.getRExpression();
    final String rExpressionText = rExpression != null ? rExpression.getText() : "";
    assignmentExpression.replace(factory.createExpressionFromText(lExpressionText + " = " + rExpressionText, psiElement));
  }
}
