package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class JavaAnonymousUnwrapper extends JavaUnwrapper {
  public JavaAnonymousUnwrapper() {
    super(CodeInsightBundle.message("unwrap.anonymous"));
  }

  public boolean isApplicableTo(PsiElement e) {
    return e instanceof PsiAnonymousClass;
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    PsiMethod[] methods = ((PsiAnonymousClass)element).getMethods();

    PsiElement parent = element.getParent() instanceof PsiNewExpression
                        ? element.getParent()
                        : element;

    for (PsiMethod m : methods) {
      context.extractFromCodeBlock(m.getBody(), parent);
    }
    context.delete(parent);
  }
}