// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.presentation;

import com.intellij.model.psi.PsiSymbolDeclaration;
import com.intellij.navigation.ItemPresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SymbolDeclarationPresentation extends ItemPresentation {

  static @Nullable SymbolDeclarationPresentation getFor(@NotNull PsiSymbolDeclaration symbolDeclaration) {
    for (SymbolDeclarationPresentationProvider declarationPresentationProvider : SymbolDeclarationPresentationProvider.EP.forKey(
      symbolDeclaration.getClass())) {
      @SuppressWarnings("unchecked")
      SymbolDeclarationPresentation presentation = declarationPresentationProvider.getPresentation(symbolDeclaration);
      if (presentation != null) {
        return presentation;
      }
    }
    return null;
  }
}
