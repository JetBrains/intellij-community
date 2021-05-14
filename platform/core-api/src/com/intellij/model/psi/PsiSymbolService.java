// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi;

import com.intellij.model.Symbol;
import com.intellij.model.SymbolResolveResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface PsiSymbolService {

  @NotNull
  static PsiSymbolService getInstance() {
    return ApplicationManager.getApplication().getService(PsiSymbolService.class);
  }

  /**
   * This method is used to adapt PsiElements to Symbol-based APIs.
   */
  @Contract(pure = true)
  @NotNull
  Symbol asSymbol(@NotNull PsiElement element);

  @Contract(pure = true)
  @NotNull PsiSymbolReference asSymbolReference(@NotNull PsiReference reference);

  @Contract(pure = true)
  @NotNull SymbolResolveResult asSymbolResolveResult(@NotNull ResolveResult result);

  /**
   * This method is used to adapt Symbols to PsiElement-based APIs.
   */
  @Contract(pure = true)
  @Nullable
  PsiElement extractElementFromSymbol(@NotNull Symbol symbol);
}
