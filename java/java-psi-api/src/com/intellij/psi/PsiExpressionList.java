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

/**
 * Represents a list of expressions separated by commas.
 *
 * @see PsiCall#getArgumentList()
 * @see PsiExpressionListStatement#getExpressionList()
 */
public interface PsiExpressionList extends PsiElement {
  /**
   * Returns the expressions contained in the list.
   *
   * @return the array of expressions contained in the list.
   */
  @NotNull PsiExpression[] getExpressions();

  @NotNull PsiType[] getExpressionTypes();

  /**
   * @return number of expressions in the expression list
   * @since 2018.1
   */
  default int getExpressionCount() {
    return getExpressions().length;
  }

  /**
   * @return true if expression list contains no expressions
   * @since 2018.1
   */
  default boolean isEmpty() {
    return getExpressionCount() == 0;
  }
}
