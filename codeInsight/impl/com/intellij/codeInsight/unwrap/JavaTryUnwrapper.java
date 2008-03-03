package com.intellij.codeInsight.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTryStatement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.editor.Editor;
import com.intellij.codeInsight.CodeInsightBundle;

public class JavaTryUnwrapper extends JavaUnwrapper {
  public JavaTryUnwrapper() {
    super(CodeInsightBundle.message("unwrap.try"));
  }

  public boolean isApplicableTo(PsiElement e) {
    return e instanceof PsiTryStatement;
  }

  public void unwrap(Editor editor, PsiElement element) throws IncorrectOperationException {
    PsiTryStatement trySt = (PsiTryStatement)element;

    extractFromCodeBlock(trySt.getTryBlock(), trySt);
    extractFromCodeBlock(trySt.getFinallyBlock(), trySt);

    trySt.delete();
  }
}
