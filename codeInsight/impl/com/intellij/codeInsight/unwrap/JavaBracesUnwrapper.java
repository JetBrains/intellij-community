package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class JavaBracesUnwrapper extends JavaUnwrapper {
  public JavaBracesUnwrapper() {
    super(CodeInsightBundle.message("unwrap.braces"));
  }

  protected boolean isAplicableToJava(PsiElement e) {
    return e instanceof PsiBlockStatement && !belongsToControlStructures(e);
  }

  private boolean belongsToControlStructures(PsiElement e) {
    PsiElement p = e.getParent();

    return p instanceof PsiIfStatement
           || p instanceof PsiWhileStatement
           || p instanceof PsiTryStatement
           || p instanceof PsiCatchSection;
  }

  public void unwrap(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
    extractFromBlockOrSingleStatement((PsiStatement)element, element);
    element.delete();
  }
}