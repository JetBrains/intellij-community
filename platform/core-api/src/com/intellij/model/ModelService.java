// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.model.psi.PsiModelElement;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface ModelService {

  @NotNull
  static ModelElement adaptPsiElement(@NotNull PsiElement element) {
    if (element instanceof ModelElement) {
      return (ModelElement)element;
    }
    else {
      return new PsiModelElement(element);
    }
  }
}
