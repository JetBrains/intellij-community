package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class JavaWhileUnwrapper extends JavaUnwrapper {
  public JavaWhileUnwrapper() {
    super(CodeInsightBundle.message("unwrap.while"));
  }

  public boolean isApplicableTo(PsiElement e) {
    return e instanceof PsiWhileStatement || e instanceof PsiDoWhileStatement;
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    PsiStatement body = ((PsiLoopStatement)element).getBody();

    context.extractFromBlockOrSingleStatement(body, element);
    context.delete(element);
  }
}