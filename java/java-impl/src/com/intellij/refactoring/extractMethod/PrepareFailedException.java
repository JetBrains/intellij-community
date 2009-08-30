package com.intellij.refactoring.extractMethod;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * @author dsl
 */
public class PrepareFailedException extends Exception {
  private final PsiFile myContainingFile;
  private final TextRange myTextRange;

  PrepareFailedException(String message, PsiElement errorElement) {
    super(message);
    myContainingFile = errorElement.getContainingFile();
    myTextRange = errorElement.getTextRange();
  }

  PsiFile getFile() {
    return myContainingFile;
  }

  TextRange getTextRange() {
    return myTextRange;
  }
}
