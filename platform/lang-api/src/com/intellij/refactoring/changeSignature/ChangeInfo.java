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

package com.intellij.refactoring.changeSignature;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the set of changes performed by a "Change Signature" refactoring.
 *
 * @author yole
 * @since 8.1
 */
public interface ChangeInfo {
  /**
   * Returns the list of parameters after the refactoring.
   *
   * @return parameter list.
   */
  @NotNull
  ParameterInfo[] getNewParameters();

  boolean isParameterSetOrOrderChanged();

  boolean isParameterTypesChanged();

  boolean isParameterNamesChanged();

  boolean isGenerateDelegate();

  boolean isNameChanged();
  
  PsiElement getMethod();

  boolean isReturnTypeChanged();

  String getNewName();

  Language getLanguage();
}
