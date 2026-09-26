// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class AnalysisCanceledException extends Exception {
  private final PsiElement myErrorElement;

  public AnalysisCanceledException(@NotNull PsiElement errorElement) {
    myErrorElement = errorElement;
  }

  public @NotNull PsiElement getErrorElement() {
    return myErrorElement;
  }
}
