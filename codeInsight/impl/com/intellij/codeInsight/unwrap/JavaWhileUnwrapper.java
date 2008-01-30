package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class JavaWhileUnwrapper extends JavaUnwrapper {
  public JavaWhileUnwrapper() {
    super(CodeInsightBundle.message("unwrap.while"));
  }

  protected boolean isAplicableToJava(PsiElement e) {
    return e instanceof PsiWhileStatement;
  }

  public void unwrap(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
    PsiStatement body = ((PsiWhileStatement)element).getBody();
    extractFromBlockOrSingleStatement(body, element);

    element.delete();
  }
}