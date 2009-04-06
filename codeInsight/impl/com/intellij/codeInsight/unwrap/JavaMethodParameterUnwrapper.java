package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.util.IncorrectOperationException;

import java.util.List;

public class JavaMethodParameterUnwrapper extends JavaUnwrapper {
  public JavaMethodParameterUnwrapper() {
    super(CodeInsightBundle.message("unwrap.method.parameter"));
  }

  public boolean isApplicableTo(PsiElement e) {
    return (e instanceof PsiExpression)
           && e.getParent() instanceof PsiExpressionList;
  }

  @Override
  public PsiElement collectAffectedElements(PsiElement e, List<PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return e.getParent().getParent();
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    PsiElement methodCall = element.getParent().getParent();
    context.extractElement(element, methodCall);
    if (methodCall.getParent() instanceof PsiExpressionList) {
      context.delete(methodCall);
    }
    else {
      context.deleteExactly(methodCall);
    }
  }
}
