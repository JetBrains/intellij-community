/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.psi.tree.IElementType;

/**
 * Represents a simple assignment (<code>a=b</code>) or a compound assignment (<code>a+=1</code>) expression.
 */
public interface PsiAssignmentExpression extends PsiExpression {
  /**
   * Returns the expression on the left side of the assignment.
   *
   * @return the left side expression.
   */
  @NotNull
  PsiExpression getLExpression();

  /**
   * Returns the expression on the right side of the assignment.
   *
   * @return the right side expression, or null if the assignment
   * expression is incomplete.
   */
  @Nullable
  PsiExpression getRExpression();

  /**
   * Returns the token representing the assignment operation ({@link JavaTokenType#EQ} for a simple
   * assignment, {@link JavaTokenType#PLUSEQ} etc. for a compound assignment).
   *
   * @return the assignment operation token.
   */
  @NotNull
  PsiJavaToken getOperationSign();

  /**
   * Returns the type of the token representing the operation performed.
   *
   * @return the token type.
   */
  @NotNull
  IElementType getOperationTokenType();
}
