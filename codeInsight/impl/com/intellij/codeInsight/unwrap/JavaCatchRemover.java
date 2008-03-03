package com.intellij.codeInsight.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiTryStatement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.editor.Editor;
import com.intellij.codeInsight.CodeInsightBundle;

public class JavaCatchRemover extends JavaUnwrapper {
  public JavaCatchRemover() {
    super(CodeInsightBundle.message("remove.catch"));
  }

  public boolean isApplicableTo(PsiElement e) {
    return e instanceof PsiCatchSection && tryHasSeveralCatches(e);
  }

  private boolean tryHasSeveralCatches(PsiElement el) {
    return ((PsiTryStatement)el.getParent()).getCatchBlocks().length > 1;
  }

  public void unwrap(Editor editor, PsiElement element) throws IncorrectOperationException {
    element.delete();
  }
}
