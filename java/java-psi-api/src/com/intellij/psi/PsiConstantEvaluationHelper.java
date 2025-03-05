// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.psi.util.ConstantEvaluationOverflowException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentMap;

/**
 * Service for evaluating values of constant expressions.
 *
 * @see JavaPsiFacade#getConstantEvaluationHelper()
 */
public abstract class PsiConstantEvaluationHelper {
  /**
   * Evaluates the value of the specified expression.
   *
   * @param expression the expression to evaluate.
   * @return the result of the evaluation, or null if the expression is not a constant expression.
   */
  @Contract("null -> null")
  public @Nullable Object computeConstantExpression(@Nullable PsiElement expression) {
    return computeConstantExpression(expression, false);
  }

  /**
   * Evaluates the value of the specified expression and optionally throws
   * {@link ConstantEvaluationOverflowException} if an overflow is detected
   * during the evaluation.
   *
   * @param expression the expression to evaluate.
   * @param throwExceptionOnOverflow if true, an exception is thrown if an overflow is detected during the evaluation.
   * @return the result of the evaluation, or null if the expression is not a constant expression.
   */
  public abstract Object computeConstantExpression(@Nullable PsiElement expression, boolean throwExceptionOnOverflow);

  public abstract Object computeExpression(@NotNull PsiExpression expression, boolean throwExceptionOnOverflow,
                                           final @Nullable AuxEvaluator auxEvaluator);

  public interface AuxEvaluator {
    Object computeExpression(@NotNull PsiExpression expression, @NotNull AuxEvaluator auxEvaluator);

    @NotNull
    ConcurrentMap<PsiElement, Object> getCacheMap(final boolean overflow);
  }
}
