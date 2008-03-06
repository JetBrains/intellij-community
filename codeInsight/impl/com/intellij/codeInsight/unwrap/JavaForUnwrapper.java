package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class JavaForUnwrapper extends JavaUnwrapper {
  public JavaForUnwrapper() {
    super(CodeInsightBundle.message("unwrap.for"));
  }

  public boolean isApplicableTo(PsiElement e) {
    return e instanceof PsiForStatement || e instanceof PsiForeachStatement;
  }

  public void unwrap(Editor editor, PsiElement element) throws IncorrectOperationException {
    if (element instanceof PsiForStatement) {
      unwrapInitializer(element);
    }
    unwrapBody(element);

    element.delete();
  }

  private void unwrapInitializer(PsiElement element) throws IncorrectOperationException {
    PsiStatement init = ((PsiForStatement)element).getInitialization();
    extractFromBlockOrSingleStatement(init, element);
  }

  private void unwrapBody(PsiElement element) throws IncorrectOperationException {
    PsiStatement body = ((PsiLoopStatement)element).getBody();
    extractFromBlockOrSingleStatement(body, element);
  }
}