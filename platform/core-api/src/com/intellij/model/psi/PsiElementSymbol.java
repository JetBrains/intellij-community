// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi;

import com.intellij.model.Symbol;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@Experimental
public final class PsiElementSymbol implements Symbol {

  private final @NotNull PsiElement myElement;

  @Contract(pure = true)
  public PsiElementSymbol(@NotNull PsiElement element) {
    myElement = element;
  }

  @Contract(pure = true)
  @NotNull
  public PsiElement getElement() {
    return myElement;
  }
}
