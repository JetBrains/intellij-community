/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.lang.jvm.JvmParameter;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the parameter of a Java method, foreach (enhanced for) statement or catch block.
 */
public interface PsiParameter extends PsiVariable, JvmParameter {
  /**
   * The empty array of PSI parameters which can be reused to avoid unnecessary allocations.
   */
  PsiParameter[] EMPTY_ARRAY = new PsiParameter[0];

  ArrayFactory<PsiParameter> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiParameter[count];

  /**
   * Returns the element (method, lambda expression, foreach statement or catch block) in which the
   * parameter is declared.
   *
   * @return the declaration scope for the parameter.
   */
  @NotNull
  PsiElement getDeclarationScope();

  /**
   * Checks if the parameter accepts a variable number of arguments.
   *
   * @return true if the parameter is a vararg, false otherwise
   */
  boolean isVarArgs();

  /**
   * {@inheritDoc}
   */
  @Override
  @Nullable
  PsiTypeElement getTypeElement();

  @NotNull
  @Override
  default PsiAnnotation[] getAnnotations() {
    return PsiVariable.super.getAnnotations();
  }
}
