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

import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java type cast expression.
 */
public interface PsiTypeCastExpression extends PsiExpression {
  /**
   * Returns the type element specifying the type to which the operand expression is cast.
   *
   * @return the type element for the cast, or null if the expression is incomplete.
   */
  @Nullable PsiTypeElement getCastType();

  /**
   * Returns the expression which is cast to the specified type.
   *
   * @return the operand of the type cast expression.
   */
  @Nullable PsiExpression getOperand();
}
