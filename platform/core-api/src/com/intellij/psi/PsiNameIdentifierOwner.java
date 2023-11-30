// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * A PSI element which has a name given by an identifier token in the PSI tree.
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
  default @Nullable PsiElement getIdentifyingElement() {
    return getNameIdentifier();
  }
}
