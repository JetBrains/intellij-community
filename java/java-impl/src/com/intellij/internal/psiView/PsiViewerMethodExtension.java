package com.intellij.internal.psiView;

import com.intellij.psi.PsiElement;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public class PsiViewerMethodExtension implements PsiViewerExtension {
  public String getName() {
    return "Java Method";
  }

  public PsiElement createElement(Project project, String text) {
    return JavaPsiFacade.getInstance(project).getElementFactory().createMethodFromText(text, null);
  }
}
