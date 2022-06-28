// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class AnalysisCanceledException extends Exception {
  private final PsiElement myErrorElement;

  public AnalysisCanceledException(@NotNull PsiElement errorElement) {
    myErrorElement = errorElement;
  }

  @NotNull
  public PsiElement getErrorElement() {
    return myErrorElement;
  }
}
