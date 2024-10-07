// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.*;
import com.intellij.refactoring.util.RefactoringUtil;

public class SafeDeleteFieldWriteReference extends SafeDeleteReferenceUsageInfo {

  public SafeDeleteFieldWriteReference(PsiAssignmentExpression expr, PsiMember referencedElement) {
    super(expr, referencedElement, safeRemoveRHS(expr));
  }

  private static boolean safeRemoveRHS(PsiAssignmentExpression expression) {
    final PsiExpression rExpression = expression.getRExpression();
    final PsiElement parent = expression.getParent();
    return RefactoringUtil.verifySafeCopyExpression(rExpression) == RefactoringUtil.EXPR_COPY_SAFE
           && parent instanceof PsiExpressionStatement statement
           && statement.getExpression() == expression;
  }

  @Override
  public void deleteElement() {
    PsiElement element = getElement();
    if (element != null) element.getParent().delete();
  }
}
