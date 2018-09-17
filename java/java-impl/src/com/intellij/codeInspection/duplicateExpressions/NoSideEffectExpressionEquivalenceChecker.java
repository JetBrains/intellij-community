// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.duplicateExpressions;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NotNull;

/**
 * @author Pavel.Dolgov
 */
class NoSideEffectExpressionEquivalenceChecker extends EquivalenceChecker {
  @Override
  protected Match newExpressionsMatch(@NotNull PsiNewExpression newExpression1,
                                      @NotNull PsiNewExpression newExpression2) {
    return EXACT_MISMATCH;
  }

  @Override
  protected Match methodCallExpressionsMatch(@NotNull PsiMethodCallExpression methodCallExpression1,
                                             @NotNull PsiMethodCallExpression methodCallExpression2) {
    if (SideEffectChecker.mayHaveSideEffects(methodCallExpression1)) {
      return EXACT_MISMATCH;
    }
    return super.methodCallExpressionsMatch(methodCallExpression1, methodCallExpression2);
  }

  @Override
  protected Match assignmentExpressionsMatch(@NotNull PsiAssignmentExpression assignmentExpression1,
                                             @NotNull PsiAssignmentExpression assignmentExpression2) {
    return EXACT_MISMATCH;
  }

  @Override
  protected Match arrayInitializerExpressionsMatch(@NotNull PsiArrayInitializerExpression arrayInitializerExpression1,
                                                   @NotNull PsiArrayInitializerExpression arrayInitializerExpression2) {
    return EXACT_MISMATCH;
  }

  @Override
  protected Match unaryExpressionsMatch(@NotNull PsiUnaryExpression unaryExpression1, @NotNull PsiUnaryExpression unaryExpression2) {
    if (isSideEffectUnaryOperator(unaryExpression1.getOperationTokenType())) {
      return EXACT_MISMATCH;
    }
    return super.unaryExpressionsMatch(unaryExpression1, unaryExpression2);
  }

  private static boolean isSideEffectUnaryOperator(IElementType tokenType) {
    return JavaTokenType.PLUSPLUS.equals(tokenType) || JavaTokenType.MINUSMINUS.equals(tokenType);
  }
}
