/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentMap;

/**
 * Service for evaluating values of constant expressions.
 *
 * @author ven
 * @see com.intellij.psi.JavaPsiFacade#getConstantEvaluationHelper()
 */
public abstract class PsiConstantEvaluationHelper {
  /**
   * Evaluates the value of the specified expression.
   *
   * @param expression the expression to evaluate.
   * @return the result of the evaluation, or null if the expression is not a constant expression.
   */
  @Nullable
  @Contract("null -> null")
  public Object computeConstantExpression(PsiElement expression) {
    return computeConstantExpression(expression, false);
  }

  /**
   * Evaluates the value of the specified expression and optionally throws
   * {@link com.intellij.psi.util.ConstantEvaluationOverflowException} if an overflow is detected
   * during the evaluation.
   *
   * @param expression the expression to evaluate.
   * @param throwExceptionOnOverflow if true, an exception is thrown if an overflow is detected during the evaluation.
   * @return the result of the evaluation, or null if the expression is not a constant expression.
   */
  public abstract Object computeConstantExpression(PsiElement expression, boolean throwExceptionOnOverflow);

  public abstract Object computeExpression(PsiExpression expression, boolean throwExceptionOnOverflow,
                                           @Nullable final AuxEvaluator auxEvaluator);

  public interface AuxEvaluator {
    Object computeExpression(final PsiExpression expression, final AuxEvaluator auxEvaluator);

    ConcurrentMap<PsiElement, Object> getCacheMap(final boolean overflow);
  }
}
