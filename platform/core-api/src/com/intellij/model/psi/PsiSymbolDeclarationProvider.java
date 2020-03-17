// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Register the implementation of this interface at {@code com.intellij.psi.declarationProvider} extension point
 * to provide symbol declarations by PsiElement.
 *
 * @see PsiSymbolDeclaration
 */
public interface PsiSymbolDeclarationProvider {

  /**
   * @param element PsiElement in the code which might represent some declaration
   */
  @NotNull
  Collection<? extends PsiSymbolDeclaration> getDeclarations(@NotNull PsiElement element, int offsetInElement);
}
