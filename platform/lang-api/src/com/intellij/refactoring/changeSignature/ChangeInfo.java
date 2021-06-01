// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.changeSignature;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Represents the set of changes performed by a "Change Signature" refactoring.
 */
public interface ChangeInfo {
  /**
   * Returns the list of parameters after the refactoring.
   *
   * @return parameter list.
   */
  ParameterInfo @NotNull [] getNewParameters();

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
