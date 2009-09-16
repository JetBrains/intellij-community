package com.intellij.codeInspection;

import com.intellij.codeInspection.reference.RefElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * User: Maxim.Mossienko
 * Date: 16.09.2009
 * Time: 20:35:06
 */
public class GlobalInspectionUtil {
  private static final String LOC_MARKER = " #loc";

  static RefElement retrieveRefElement(PsiElement element, GlobalInspectionContext globalContext) {
    PsiFile elementFile = element.getContainingFile();
    RefElement refElement = globalContext.getRefManager().getReference(elementFile);
    if (refElement == null) {
      PsiElement context = elementFile.getContext();
      if (context != null) refElement = globalContext.getRefManager().getReference(context.getContainingFile());
    }
    return refElement;
  }

  public static String createInspectionMessage(String message) {
    return message + LOC_MARKER;
  }
}
