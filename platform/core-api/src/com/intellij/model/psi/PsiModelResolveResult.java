// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi;

import com.intellij.model.ModelElement;
import com.intellij.model.ModelResolveResult;
import com.intellij.model.ModelService;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public final class PsiModelResolveResult implements ModelResolveResult {

  private final @NotNull ModelElement myElement;

  public PsiModelResolveResult(@NotNull PsiElement element) {
    myElement = ModelService.adaptPsiElement(element);
  }

  @NotNull
  @Override
  public ModelElement getResolvedElement() {
    return myElement;
  }
}
