// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiElement;

public class AnalysisCanceledException extends Exception {
  private final PsiElement myErrorElement;

  public AnalysisCanceledException(PsiElement errorElement) {
    myErrorElement = errorElement;
  }

  public PsiElement getErrorElement() {
    return myErrorElement;
  }
}
