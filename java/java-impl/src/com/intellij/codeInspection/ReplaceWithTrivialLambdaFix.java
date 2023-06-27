// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ReplaceWithTrivialLambdaFix extends PsiUpdateModCommandQuickFix {
  private final String myValue;

  public ReplaceWithTrivialLambdaFix(Object value) {
    myValue = String.valueOf(value);
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return JavaBundle.message("inspection.replace.with.trivial.lambda.fix.name", myValue);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("inspection.replace.with.trivial.lambda.fix.family.name");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiMethodReferenceExpression methodRef = ObjectUtils.tryCast(element, PsiMethodReferenceExpression.class);
    if (methodRef == null) return;
    PsiLambdaExpression lambdaExpression = LambdaRefactoringUtil.convertMethodReferenceToLambda(methodRef, true, true);
    if (lambdaExpression == null) return;
    PsiElement body = lambdaExpression.getBody();
    if (body == null) return;
    body.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(myValue, lambdaExpression));
  }
}
