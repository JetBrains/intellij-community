package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;

public class PsiElementResult implements Result {
  private SmartPsiElementPointer myPointer = null;

  public PsiElementResult(PsiElement element) {
    if (element != null) {
      myPointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
    }
  }

  public PsiElement getElement() {
    return myPointer != null ? myPointer.getElement() : null;
  }

  public boolean equalsToText(String text, PsiElement context) {
    return text.equals(toString());
  }

  public String toString() {
    PsiElement element = getElement();
    if (element != null) {
      return element.getText();
    }
    return null;
  }

  public void handleFocused(final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd) {
  }
}