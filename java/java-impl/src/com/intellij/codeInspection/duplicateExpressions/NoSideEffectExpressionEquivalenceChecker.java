// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.duplicateExpressions;

import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiUnaryExpression;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NotNull;

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
}
