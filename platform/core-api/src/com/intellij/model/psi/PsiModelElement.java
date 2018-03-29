// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi;

import com.intellij.model.ModelElement;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public final class PsiModelElement extends UserDataHolderBase implements ModelElement {

  private final @NotNull PsiElement myElement;

  public PsiModelElement(@NotNull PsiElement element) {
    myElement = element;
  }

  @NotNull
  public PsiElement getElement() {
    return myElement;
  }
}
