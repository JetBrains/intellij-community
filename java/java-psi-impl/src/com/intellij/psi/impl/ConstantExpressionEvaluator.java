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
package com.intellij.psi.impl;

import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.ConstantEvaluationOverflowException;
import org.jetbrains.annotations.Nullable;

/**
 * An extension for JVM languages that evaluates the compile-time constant expression
 * to Java object (e.g., number, string, boolean, etc.).
 */
public interface ConstantExpressionEvaluator {
  /**
   * Evaluates the value of the specified expression and optionally throws
   * {@link ConstantEvaluationOverflowException} if an overflow is detected
   * during the evaluation.
   *
   * @param expression               the expression to evaluate.
   * @param throwExceptionOnOverflow if true, an exception is thrown if an overflow is detected during the evaluation.
   * @return the result of the evaluation, or null if the expression is not a constant expression.
   */
  @Nullable Object computeConstantExpression(PsiElement expression, boolean throwExceptionOnOverflow);

  /**
   * Evaluates the value of the specified expression and optionally throws
   * {@link ConstantEvaluationOverflowException} if an overflow is detected
   * during the evaluation. Some Java expressions, like method calls are delegated to
   * auxEvaluator.
   *
   * @param expression               the expression to evaluate.
   * @param throwExceptionOnOverflow if true, an exception is thrown if an overflow is detected during the evaluation.
   * @param auxEvaluator             an evaluator to evaluate the value of non-constant parts like method calls.
   * @return the result of the evaluation, or null if the expression is not a constant expression.
   */
  @Nullable Object computeExpression(PsiElement expression,
                                     boolean throwExceptionOnOverflow,
                                     @Nullable PsiConstantEvaluationHelper.AuxEvaluator auxEvaluator);
}
