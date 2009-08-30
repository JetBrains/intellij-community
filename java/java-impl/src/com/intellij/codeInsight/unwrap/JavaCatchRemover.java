package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.PsiCatchSection;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.util.IncorrectOperationException;

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

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    context.delete(element);
  }
}
