package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class JavaSynchronizedUnwrapper extends JavaUnwrapper {
  public JavaSynchronizedUnwrapper() {
    super(CodeInsightBundle.message("unwrap.synchronized"));
  }

  public boolean isApplicableTo(PsiElement e) {
    return e instanceof PsiSynchronizedStatement;
  }

  public void unwrap(Editor editor, PsiElement element) throws IncorrectOperationException {
    PsiCodeBlock body = ((PsiSynchronizedStatement)element).getBody();
    extractFromCodeBlock(body, element);

    element.delete();
  }
}