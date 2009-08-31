package com.intellij.psi.impl;

import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * @author Serega.Vasiliev
 */
public interface ConstantExpressionEvaluator {
  Object computeConstantExpression(PsiElement expression, boolean throwExceptionOnOverflow);

  Object computeExpression(PsiElement expression,
                           boolean throwExceptionOnOverflow,
                           @Nullable PsiConstantEvaluationHelper.AuxEvaluator auxEvaluator);
}
