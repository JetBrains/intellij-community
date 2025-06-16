// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.psi;

import com.intellij.model.Symbol;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

final class ImmediatePsiSymbolReference implements PsiSymbolReference {

  private final @NotNull PsiElement myElement;
  private final @NotNull Collection<? extends @NotNull Symbol> myTargets;

  ImmediatePsiSymbolReference(@NotNull PsiElement element, @NotNull Collection<? extends @NotNull Symbol> targets) {
    myElement = element;
    myTargets = targets;
  }

  @Override
  public @NotNull PsiElement getElement() {
    return myElement;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    return TextRange.from(0, myElement.getTextLength());
  }

  @Override
  public @NotNull @Unmodifiable Collection<? extends Symbol> resolveReference() {
    return myTargets;
  }
}
