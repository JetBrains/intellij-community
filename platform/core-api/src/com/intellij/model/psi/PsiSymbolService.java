// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.psi;

import com.intellij.model.Symbol;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface PsiSymbolService {

  static @NotNull PsiSymbolService getInstance() {
    return ApplicationManager.getApplication().getService(PsiSymbolService.class);
  }

  /**
   * This method is used to adapt PsiElements to Symbol-based APIs.
   */
  @Contract(pure = true)
  @NotNull Symbol asSymbol(@NotNull PsiElement element);

  @Contract(pure = true)
  @NotNull PsiSymbolReference asSymbolReference(@NotNull PsiReference reference);

  /**
   * This method is used to adapt Symbols to PsiElement-based APIs.
   */
  @Contract(pure = true)
  @Nullable PsiElement extractElementFromSymbol(@NotNull Symbol symbol);
}
