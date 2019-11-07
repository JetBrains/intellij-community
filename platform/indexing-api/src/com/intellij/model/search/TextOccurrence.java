// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public interface TextOccurrence {

  @NotNull
  PsiElement getElement();

  int getOffsetInElement();

  @Contract(value = "_, _ -> new", pure = true)
  @NotNull
  static TextOccurrence of(@NotNull PsiElement element, int offsetInElement) {
    return new Impl(element, offsetInElement);
  }
}

class Impl implements TextOccurrence {

  private final PsiElement myElement;
  private final int myOffsetInElement;

  Impl(PsiElement element, int offsetInElement) {
    myElement = element;
    myOffsetInElement = offsetInElement;
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return myElement;
  }

  @Override
  public int getOffsetInElement() {
    return myOffsetInElement;
  }
}
