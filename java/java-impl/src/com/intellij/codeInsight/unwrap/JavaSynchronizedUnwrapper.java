package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSynchronizedStatement;
import com.intellij.util.IncorrectOperationException;

public class JavaSynchronizedUnwrapper extends JavaUnwrapper {
  public JavaSynchronizedUnwrapper() {
    super(CodeInsightBundle.message("unwrap.synchronized"));
  }

  public boolean isApplicableTo(PsiElement e) {
    return e instanceof PsiSynchronizedStatement;
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    PsiCodeBlock body = ((PsiSynchronizedStatement)element).getBody();
    context.extractFromCodeBlock(body, element);

    context.delete(element);
  }
}