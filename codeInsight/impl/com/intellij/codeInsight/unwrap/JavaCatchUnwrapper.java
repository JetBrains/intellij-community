package com.intellij.codeInsight.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiTryStatement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.codeInsight.CodeInsightBundle;

public class JavaCatchUnwrapper extends JavaUnwrapper {
  public JavaCatchUnwrapper() {
    super(CodeInsightBundle.message("unwrap.catch"));
  }

  protected boolean isAplicableToJava(PsiElement e) {
    return e instanceof PsiCatchSection && tryHasSeveralCatches(e);
  }

  private boolean tryHasSeveralCatches(PsiElement el) {
    return ((PsiTryStatement)el.getParent()).getCatchBlocks().length > 1;
  }

  public void unwrap(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
    element.delete();
  }
}
