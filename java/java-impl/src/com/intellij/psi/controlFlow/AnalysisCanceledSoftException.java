package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiElement;

/**
 * @author mike
 */
class AnalysisCanceledSoftException extends RuntimeException {
  private final PsiElement myErrorElement;

  public AnalysisCanceledSoftException(PsiElement errorElement) {
    myErrorElement = errorElement;
  }

  public PsiElement getErrorElement() {
    return myErrorElement;
  }

}
