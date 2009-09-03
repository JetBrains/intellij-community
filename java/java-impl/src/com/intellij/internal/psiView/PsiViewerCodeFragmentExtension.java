package com.intellij.internal.psiView;

import com.intellij.psi.PsiElement;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public class PsiViewerCodeFragmentExtension implements PsiViewerExtension {
  public String getName() {
    return "Java Code Block";
  }

  public PsiElement createElement(Project project, String text) {
    return JavaPsiFacade.getElementFactory(project).createCodeBlockFromText(text, null);
  }
}
