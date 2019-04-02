// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.annotations.ApiStatus.Experimental;

@Experimental
public interface PsiSymbolReference extends SymbolReference {

  @NotNull
  PsiElement getElement();

  @NotNull
  TextRange getRangeInElement();
}
