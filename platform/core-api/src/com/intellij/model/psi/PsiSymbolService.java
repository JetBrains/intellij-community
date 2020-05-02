// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi;

import com.intellij.model.Symbol;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface PsiSymbolService {

  @NotNull
  static PsiSymbolService getInstance() {
    return ServiceManager.getService(PsiSymbolService.class);
  }

  /**
   * This method is used to adapt PsiElements to Symbol-based APIs.
   */
  @Contract(pure = true)
  @NotNull
  Symbol asSymbol(@NotNull PsiElement element);

  /**
   * This method is used to adapt Symbols to PsiElement-based APIs.
   */
  @Contract(pure = true)
  @Nullable
  PsiElement extractElementFromSymbol(@NotNull Symbol symbol);
}
