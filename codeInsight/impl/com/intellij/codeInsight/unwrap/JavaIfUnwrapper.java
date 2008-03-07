package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.util.IncorrectOperationException;

public class JavaIfUnwrapper extends JavaUnwrapper {
  public JavaIfUnwrapper() {
    super(CodeInsightBundle.message("unwrap.if"));
  }

  public boolean isApplicableTo(PsiElement e) {
    return e instanceof PsiIfStatement && !isElseBlock(e);
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    PsiStatement then = ((PsiIfStatement)element).getThenBranch();
    context.extractFromBlockOrSingleStatement(then, element);

    context.delete(element);
  }
}
