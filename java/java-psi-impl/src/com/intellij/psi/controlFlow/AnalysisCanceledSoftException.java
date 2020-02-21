// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiElement;

class AnalysisCanceledSoftException extends RuntimeException {
  private final PsiElement myErrorElement;

  AnalysisCanceledSoftException(PsiElement errorElement) {
    myErrorElement = errorElement;
  }

  public PsiElement getErrorElement() {
    return myErrorElement;
  }

}
