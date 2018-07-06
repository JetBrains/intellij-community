// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi;

import com.intellij.model.Symbol;
import com.intellij.model.SymbolResolveResult;
import com.intellij.model.SymbolService;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public final class PsiSymbolResolveResult implements SymbolResolveResult {

  private final @NotNull Symbol myElement;

  public PsiSymbolResolveResult(@NotNull PsiElement element) {
    myElement = SymbolService.adaptPsiElement(element);
  }

  @NotNull
  @Override
  public Symbol getTarget() {
    return myElement;
  }
}
