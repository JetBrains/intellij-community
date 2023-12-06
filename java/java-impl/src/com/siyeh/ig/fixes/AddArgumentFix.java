// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class AddArgumentFix extends PsiUpdateModCommandQuickFix {
  private final String myExpressionText;
  private final String myPresentableText;

  public AddArgumentFix(String expressionText, String presentableText) {
    myExpressionText = expressionText;
    myPresentableText = presentableText;
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiReferenceExpression ref = PsiTreeUtil.getParentOfType(element, PsiReferenceExpression.class);
    if (ref == null) return;
    PsiMethodCallExpression call;
    if (ref instanceof PsiMethodReferenceExpression) {
      PsiLambdaExpression lambda = LambdaRefactoringUtil.convertMethodReferenceToLambda((PsiMethodReferenceExpression)ref, true, true);
      if (lambda == null) return;
      call = ObjectUtils.tryCast(lambda.getBody(), PsiMethodCallExpression.class);
    } else {
      call = ObjectUtils.tryCast(ref.getParent(), PsiMethodCallExpression.class);
    }
    if (call == null) return;
    PsiElement result = call.getArgumentList().add(JavaPsiFacade.getElementFactory(project).createExpressionFromText(myExpressionText, call));
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return InspectionGadgetsBundle.message("fix.add.argument.name", myPresentableText);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("fix.add.argument.family.name");
  }
}
