package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.JavaTemplateUtil;
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
    String text = null;
    PsiElement element = getElement();
    if (element != null) {
      if (element instanceof PsiVariable) {
        text = ((PsiVariable)element).getName();
      }
      else if (element instanceof PsiMethod) {
        text = ((PsiMethod)element).getName() + "()";
      }
      else if (element instanceof PsiClass) {
        PsiIdentifier identifier = ((PsiClass)element).getNameIdentifier();
        if (identifier == null) return "";
        text = identifier.getText();
      }
      else {
        text = element.getText();
      }
    }
    return text;
  }

  public void handleFocused(final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd) {
    JavaTemplateUtil.updateTypeBindings(getElement(), psiFile, document, segmentStart, segmentEnd);
  }
}