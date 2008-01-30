package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.util.IncorrectOperationException;

public class JavaForUnwrapper extends JavaUnwrapper {
  public JavaForUnwrapper() {
    super(CodeInsightBundle.message("unwrap.for"));
  }

  public boolean isApplicableTo(PsiElement e) {
    return e instanceof PsiForStatement;
  }

  public void unwrap(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
    PsiStatement init = ((PsiForStatement)element).getInitialization();
    PsiStatement body = ((PsiForStatement)element).getBody();

    extractFromBlockOrSingleStatement(init, element);
    extractFromBlockOrSingleStatement(body, element);

    element.delete();
  }
}