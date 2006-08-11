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
import com.intellij.psi.tree.IElementType;

/**
 * Represents a Java postfix increment or decrement expression.
 */
public interface PsiPostfixExpression extends PsiExpression {
  /**
   * Returns the expression representing the operand of the increment or decrement.
   *
   * @return the operand expression.
   */
  @NotNull
  PsiExpression getOperand();

  /**
   * Returns the token representing the operation performed (of type {@link JavaTokenType#PLUSPLUS} or
   * {@link JavaTokenType#MINUSMINUS}).
   *
   * @return the token for the operation performed.
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
