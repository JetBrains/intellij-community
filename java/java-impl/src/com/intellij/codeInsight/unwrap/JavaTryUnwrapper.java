package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.util.IncorrectOperationException;

public class JavaTryUnwrapper extends JavaUnwrapper {
  public JavaTryUnwrapper() {
    super(CodeInsightBundle.message("unwrap.try"));
  }

  public boolean isApplicableTo(PsiElement e) {
    return e instanceof PsiTryStatement;
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    PsiTryStatement trySt = (PsiTryStatement)element;

    context.extractFromCodeBlock(trySt.getTryBlock(), trySt);
    context.extractFromCodeBlock(trySt.getFinallyBlock(), trySt);

    context.delete(trySt);
  }
}
