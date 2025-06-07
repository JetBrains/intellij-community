// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.search;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public sealed interface TextOccurrence {

  @NotNull
  PsiElement getElement();

  int getOffsetInElement();

  @Contract(value = "_, _ -> new", pure = true)
  static @NotNull TextOccurrence of(@NotNull PsiElement element, int offsetInElement) {
    return new Impl(element, offsetInElement);
  }
}

final class Impl implements TextOccurrence {

  private final PsiElement myElement;
  private final int myOffsetInElement;

  Impl(PsiElement element, int offsetInElement) {
    myElement = element;
    myOffsetInElement = offsetInElement;
  }

  @Override
  public @NotNull PsiElement getElement() {
    return myElement;
  }

  @Override
  public int getOffsetInElement() {
    return myOffsetInElement;
  }
}
