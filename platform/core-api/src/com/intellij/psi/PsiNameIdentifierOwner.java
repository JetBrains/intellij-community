// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * A Psi element which has a name given by an identifier token in the Psi tree.
 * <p/>
 * Implementors should also override {@link PsiElement#getTextOffset()} to return
 * the relative offset of the identifier token.
 */
public interface PsiNameIdentifierOwner extends PsiNamedElement {

  @Nullable
  PsiElement getNameIdentifier();

  /**
   * @return element to be used in reference equality checks
   */
  @Nullable
  default PsiElement getIdentifyingElement() {
    return getNameIdentifier();
  }
}
