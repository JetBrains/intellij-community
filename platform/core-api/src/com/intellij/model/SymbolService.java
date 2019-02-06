// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.model.psi.PsiElementSymbol;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@Experimental
public interface SymbolService {

  @Contract(pure = true)
  @NotNull
  static Symbol adaptPsiElement(@NotNull PsiElement element) {
    if (element instanceof Symbol) {
      return (Symbol)element;
    }
    else {
      return new PsiElementSymbol(element);
    }
  }

  @Contract(pure = true)
  @NotNull
  static SymbolResolveResult resolveResult(@NotNull PsiElement element) {
    Symbol symbol = adaptPsiElement(element);
    return new SymbolResolveResult() {
      @NotNull
      @Override
      public Symbol getTarget() {
        return symbol;
      }
    };
  }
}
