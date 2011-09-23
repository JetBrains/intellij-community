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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java conditional expression (for example, <code>a ? 1 : 2</code>.
 */
public interface PsiConditionalExpression extends PsiExpression {
  /**
   * Returns the expression representing the condition part of the expression.
   *
   * @return the condition expression.
   */
  @NotNull
  PsiExpression getCondition();

  /**
   * Returns the expression which is the result used when the condition is true.
   *
   * @return the true result expression, or null if the conditional expression is incomplete.
   */
  @Nullable
  PsiExpression getThenExpression();

  /**
   * Returns the expression which is the result used when the condition is false.
   *
   * @return the false result expression, or null if the conditional expression is incomplete.
   */
  @Nullable
  PsiExpression getElseExpression();
}
