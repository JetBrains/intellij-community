package com.intellij.codeInsight.unwrap;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;

public class JavaIfUnwrapper extends JavaUnwrapper {
  public JavaIfUnwrapper() {
    super("'If' statement");
  }

  protected boolean isAplicableToJava(PsiElement e) {
    return e instanceof PsiIfStatement;
  }

  public void unwrap(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
    PsiStatement then = ((PsiIfStatement)element).getThenBranch();

    if (then instanceof PsiBlockStatement) {
      extractFromCodeBlock(((PsiBlockStatement)then).getCodeBlock(), element);
    }
    else if (then != null && !(then instanceof PsiEmptyStatement)) {
      extract(new PsiElement[]{then}, element);
    }

    element.delete();
  }
}
