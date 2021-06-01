// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.presentation;

import com.intellij.model.psi.PsiSymbolDeclaration;
import com.intellij.navigation.ItemPresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SymbolDeclarationPresentation extends ItemPresentation {

  @Nullable
  static SymbolDeclarationPresentation getFor(@NotNull PsiSymbolDeclaration symbolDeclaration) {
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
