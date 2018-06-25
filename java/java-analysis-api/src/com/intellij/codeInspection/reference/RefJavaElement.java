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

package com.intellij.codeInspection.reference;

import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface RefJavaElement extends RefElement {
   /**
   * Returns the collection of references used in this element.
   * @return the collection of used types
   */
  @NotNull
  Collection<RefClass> getOutTypeReferences();


  /**
   * Checks if the element is {@code final}.
   *
   * @return true if the element is final, false otherwise.
   */
  boolean isFinal();

  /**
   * Checks if the element is {@code static}.
   *
   * @return true if the element is static, false otherwise.
   */
  boolean isStatic();

  /**
   * Checks if the element is, or belongs to, a synthetic class or method created for a JSP page.
   *
   * @return true if the element is a synthetic JSP element, false otherwise.
   */
  boolean isSyntheticJSP();

  /**
   * Returns the access modifier for the element, as one of the keywords from the
   * {@link PsiModifier} class.
   *
   * @return the modifier, or null if the element does not have any access modifier.
   */
  @NotNull
  @PsiModifier.ModifierConstant
  String getAccessModifier();
}
