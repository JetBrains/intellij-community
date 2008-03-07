package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class JavaForUnwrapper extends JavaUnwrapper {
  public JavaForUnwrapper() {
    super(CodeInsightBundle.message("unwrap.for"));
  }

  public boolean isApplicableTo(PsiElement e) {
    return e instanceof PsiForStatement || e instanceof PsiForeachStatement;
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    if (element instanceof PsiForStatement) {
      unwrapInitializer(element, context);
    }
    unwrapBody(element, context);

    context.delete(element);
  }

  private void unwrapInitializer(PsiElement element, Context context) throws IncorrectOperationException {
    PsiStatement init = ((PsiForStatement)element).getInitialization();
    context.extractFromBlockOrSingleStatement(init, element);
  }

  private void unwrapBody(PsiElement element, Context context) throws IncorrectOperationException {
    PsiStatement body = ((PsiLoopStatement)element).getBody();
    context.extractFromBlockOrSingleStatement(body, element);
  }
}