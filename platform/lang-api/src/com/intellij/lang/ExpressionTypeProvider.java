/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.lang;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @see com.intellij.codeInsight.hint.actions.ShowExpressionTypeAction
 *
 * @author gregsh
 */
public abstract class ExpressionTypeProvider<T extends PsiElement> {
  /**
   * Returns HTML string for type info hint.
   * @see com.intellij.openapi.util.text.StringUtil#escapeXml(String)
   */
  @NotNull
  public abstract String getInformationHint(@NotNull T element);

  /**
   * Returns HTML string if no target found at position.
   */
  @NotNull
  public abstract String getErrorHint();

  /**
   * Returns the list of all possible targets at specified position.
   */
  @NotNull
  public abstract List<T> getExpressionsAt(@NotNull PsiElement elementAt);

  /**
   * @return true if this type provider can provide more useful information (e.g. value range, nullability, etc.)
   * on elements via {@link #getAdvancedInformationHint(PsiElement)}.
   */
  public boolean hasAdvancedInformation() {
    return false;
  }

  /**
   * Returns HTML string containing advanced type information hint (e.g. nullability, values range, etc.)
   *
   * @param element an element to provide information about
   * @return an advanced information hint. Should return the same result as {@link #getInformationHint(PsiElement)}
   * if no additional information is available for given element.
   * @throws UnsupportedOperationException if this provider does not provide any advanced information
   *                                       (in this case {@link #hasAdvancedInformation()} method must return false).
   */
  @NotNull
  public String getAdvancedInformationHint(@NotNull T element) {
    throw new UnsupportedOperationException();
  }
}
