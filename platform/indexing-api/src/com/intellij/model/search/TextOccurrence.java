// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface TextOccurrence {

  @NotNull
  PsiElement getElement();

  int getOffsetInElement();

  @NotNull
  static TextOccurrence of(@NotNull PsiElement element, int offset) {
    return new TextOccurrence() {
      @NotNull
      @Override
      public PsiElement getElement() {
        return element;
      }

      @Override
      public int getOffsetInElement() {
        return offset;
      }
    };
  }
}
