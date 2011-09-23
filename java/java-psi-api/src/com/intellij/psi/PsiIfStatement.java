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

import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a Java <code>if</code> or <code>if ... else</code> statement.
 */
public interface PsiIfStatement extends PsiStatement {
  /**
   * Returns the expression representing the condition of the statement.
   *
   * @return the expression instance, or null if the statement is incomplete.
   */
  @Nullable
  PsiExpression getCondition();

  /**
   * Returns the statement which is executed when the condition is true.
   *
   * @return the statement instance, or null if the statement is incomplete.
   */
  @Nullable
  PsiStatement getThenBranch();

  /**
   * Returns the statement which is executed when the condition is true.
   *
   * @return the statement instance, or null if the statement has no <code>else</code>
   * part or is incomplete.
   */
  @Nullable
  PsiStatement getElseBranch();

  /**
   * Returns the <code>else</code> keyword of the statement.
   *
   * @return the keyword instance, or null if the statement has no <code>else</code>
   * part.
   */
  @Nullable
  PsiKeyword getElseElement();

  /**
   * Sets the statement which is executed when the condition is false to the specified value.
   * Adds the <code>else</code> keyword if required.
   *
   * @param statement the statement to use as the else branch.
   * @throws IncorrectOperationException if the modification fails for some reason (for example,
   * the containing file is read-only).
   */
  void setElseBranch(@NotNull PsiStatement statement) throws IncorrectOperationException;

  /**
   * Sets the statement which is executed when the condition is true to the specified value.
   * Adds the parentheses if required.
   *
   * @param statement the statement to use as the then branch.
   * @throws IncorrectOperationException if the modification fails for some reason (for example,
   * the containing file is read-only).
   */
  void setThenBranch(@NotNull PsiStatement statement) throws IncorrectOperationException;

  /**
   * Returns the opening parenthesis enclosing the statement condition.
   *
   * @return the opening parenthesis, or null if the statement is incomplete.
   */
  @Nullable
  PsiJavaToken getLParenth();

  /**
   * Returns the closing parenthesis enclosing the statement condition.
   *
   * @return the closing parenthesis, or null if the statement is incomplete.
   */
  @Nullable
  PsiJavaToken getRParenth();
}
