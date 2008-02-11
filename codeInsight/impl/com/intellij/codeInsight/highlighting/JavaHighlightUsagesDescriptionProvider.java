package com.intellij.codeInsight.highlighting;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.lang.LangBundle;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class JavaHighlightUsagesDescriptionProvider implements ElementDescriptionProvider {
  public String getElementDescription(final PsiElement element, @Nullable final ElementDescriptionLocation location) {
    if (!(location instanceof HighlightUsagesDescriptionLocation)) return null;

    String elementName = null;
    if (element instanceof PsiClass) {
      elementName = ((PsiClass)element).getQualifiedName();
      if (elementName == null) {
        elementName = ((PsiClass)element).getName();
      }
      elementName = (((PsiClass)element).isInterface() ?
                     LangBundle.message("java.terms.interface") :
                     LangBundle.message("java.terms.class")) + " " + elementName;
    }
    else if (element instanceof PsiMethod) {
      elementName = PsiFormatUtil.formatMethod((PsiMethod)element,
                                               PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS |
                                                                     PsiFormatUtil.SHOW_CONTAINING_CLASS,
                                               PsiFormatUtil.SHOW_TYPE);
      elementName = LangBundle.message("java.terms.method") + " " + elementName;
    }
    else if (element instanceof PsiVariable) {
      elementName = PsiFormatUtil.formatVariable((PsiVariable)element,
                                                 PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_CONTAINING_CLASS,
                                                 PsiSubstitutor.EMPTY);
      if (element instanceof PsiField) {
        elementName = LangBundle.message("java.terms.field") + " " + elementName;
      }
      else if (element instanceof PsiParameter) {
        elementName = LangBundle.message("java.terms.parameter") + " " + elementName;
      }
      else {
        elementName = LangBundle.message("java.terms.variable") + " " + elementName;
      }
    }
    else if (element instanceof PsiPackage) {
      elementName = ((PsiPackage)element).getQualifiedName();
      elementName = LangBundle.message("java.terms.package") + " " + elementName;
    }
    return elementName;
  }
}
