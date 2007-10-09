/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
 * Represents the parameter of a Java method, foreach (enhanced for) statement or catch block.
 */
public interface PsiParameter extends PsiVariable {
  /**
   * The empty array of PSI parameters which can be reused to avoid unnecessary allocations.
   */
  PsiParameter[] EMPTY_ARRAY = new PsiParameter[0];

  /**
   * Returns the element (method, foreach statement or catch block) in which the
   * parameter is declared.
   *
   * @return the declaration scope for the parameter.
   */
  @NotNull
  PsiElement getDeclarationScope();

  /**
   * Checks if the parameter accepts a variable number of arguments.
   *
   * @return true if the parameter is varargs, false otherwise
   */
  boolean isVarArgs();

  /**
   * Returns the list of annotations for the parameter. Using this method is more
   * efficient than calling <code>getModifierList().getAnnotations()</code> because
   * it uses cached data and does not require parsing of the method body.
   *
   * @return the list of annotations.
   */
  @NotNull PsiAnnotation[] getAnnotations();


  /**
   * {@inheritDoc}
   */
  @NotNull
  PsiTypeElement getTypeElement();
}
