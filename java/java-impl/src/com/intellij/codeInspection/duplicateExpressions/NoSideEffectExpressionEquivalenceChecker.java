// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.duplicateExpressions;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
class NoSideEffectExpressionEquivalenceChecker extends EquivalenceChecker {

  @Override
  protected Match assignmentExpressionsMatch(@NotNull PsiAssignmentExpression assignmentExpression1,
                                             @NotNull PsiAssignmentExpression assignmentExpression2) {
    return EXACT_MISMATCH;
  }

  @Override
  protected Match unaryExpressionsMatch(@NotNull PsiUnaryExpression unaryExpression1, @NotNull PsiUnaryExpression unaryExpression2) {
    if (PsiUtil.isIncrementDecrementOperation(unaryExpression1)) {
      return EXACT_MISMATCH;
    }
    return super.unaryExpressionsMatch(unaryExpression1, unaryExpression2);
  }

  @Override
  protected boolean equivalentDeclarations(PsiElement element1, PsiElement element2) {
    return super.equivalentDeclarations(element1, element2) || pathConstructionMethod(element1) && pathConstructionMethod(element2);
  }
  
  private static boolean pathConstructionMethod(@Nullable PsiElement element) {
    PsiMethod psiMethod = ObjectUtils.tryCast(element, PsiMethod.class);
    if (psiMethod == null) return false;
    if ("of".equals(psiMethod.getName())) {
      PsiClass containingClass = psiMethod.getContainingClass();
      return containingClass != null && "java.nio.file.Path".equals(containingClass.getQualifiedName());
    }
    if ("get".equals(psiMethod.getName())) {
      PsiClass containingClass = psiMethod.getContainingClass();
      return containingClass != null && "java.nio.file.Paths".equals(containingClass.getQualifiedName());
    }
    return false;
  }
}
