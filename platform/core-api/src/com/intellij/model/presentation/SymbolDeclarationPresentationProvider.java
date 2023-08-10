// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.presentation;

import com.intellij.model.psi.PsiSymbolDeclaration;
import com.intellij.openapi.util.ClassExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SymbolDeclarationPresentationProvider<D extends PsiSymbolDeclaration> {

  ClassExtension<SymbolDeclarationPresentationProvider> EP = new ClassExtension<>("com.intellij.symbolDeclarationPresentationProvider");

  /**
   * @see SymbolDeclarationPresentation#getFor
   */
  @Nullable SymbolDeclarationPresentation getPresentation(@NotNull D symbolDeclaration);
}
