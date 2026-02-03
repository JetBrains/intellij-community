// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.psi.impl;

import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.model.psi.PsiSymbolService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PsiSymbolServiceImpl implements PsiSymbolService {

  @Contract(pure = true)
  @Override
  public @NotNull Symbol asSymbol(@NotNull PsiElement element) {
    // consider all PsiElements obtained from references (or other APIs) as Symbols,
    // because that's what usually was meant
    return new Psi2Symbol(element);
  }

  @Override
  public @NotNull PsiSymbolReference asSymbolReference(@NotNull PsiReference reference) {
    return new Psi2SymbolReference(reference);
  }

  @Contract(pure = true)
  @Override
  public @Nullable PsiElement extractElementFromSymbol(@NotNull Symbol symbol) {
    if (symbol instanceof Psi2Symbol) {
      return ((Psi2Symbol)symbol).getElement();
    }
    else {
      // If the Symbol is brand new,
      // then the client should implement proper Symbol-based APIs,
      // hence we consider brand-new Symbol implementations as inapplicable for old APIs
      return null;
    }
  }
}
