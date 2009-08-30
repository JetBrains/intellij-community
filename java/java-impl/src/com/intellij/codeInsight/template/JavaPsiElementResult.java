package com.intellij.codeInsight.template;

import com.intellij.psi.*;
import com.intellij.openapi.editor.Document;
import com.intellij.codeInsight.template.impl.JavaTemplateUtil;

/**
 * @author yole
 */
public class JavaPsiElementResult extends PsiElementResult {
  public JavaPsiElementResult(PsiElement element) {
    super(element);
  }

  public String toString() {
    PsiElement element = getElement();
    if (element != null) {
      if (element instanceof PsiVariable) {
        return ((PsiVariable)element).getName();
      }
      else if (element instanceof PsiMethod) {
        return ((PsiMethod)element).getName() + "()";
      }
      else if (element instanceof PsiClass) {
        PsiIdentifier identifier = ((PsiClass)element).getNameIdentifier();
        if (identifier == null) return "";
        return identifier.getText();
      }
    }
    return super.toString();
  }

  public void handleFocused(final PsiFile psiFile, final Document document, final int segmentStart, final int segmentEnd) {
    JavaTemplateUtil.updateTypeBindings(getElement(), psiFile, document, segmentStart, segmentEnd);
  }
}
