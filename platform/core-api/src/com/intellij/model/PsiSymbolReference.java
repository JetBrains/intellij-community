// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a reference in the source code.
 */
public interface PsiSymbolReference extends SymbolReference {

  /**
   * @return the underlying (referencing) element of the reference.
   */
  @NotNull
  PsiElement getElement();

  /**
   * @return the part of the underlying element which serves as a reference, or the complete
   * text range of the element if the entire element is a reference.
   */
  @NotNull
  TextRange getRangeInElement();
}
