package com.intellij.codeInsight.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;

public class JavaTryUnwrapper extends JavaUnwrapper {
  public JavaTryUnwrapper() {
    super("'Try' statement");
  }

  protected boolean isAplicableToJava(PsiElement e) {
    return e instanceof PsiTryStatement;
  }

  public void unwrap(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
    PsiTryStatement trySt = (PsiTryStatement)element;

    extractFromCodeBlock(trySt.getTryBlock(), trySt);
    extractFromCodeBlock(trySt.getFinallyBlock(), trySt);

    trySt.delete();
  }
}
