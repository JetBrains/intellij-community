package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class JavaWhileUnwrapper extends JavaUnwrapper {
  public JavaWhileUnwrapper() {
    super(CodeInsightBundle.message("unwrap.while"));
  }

  public boolean isApplicableTo(PsiElement e) {
    return e instanceof PsiWhileStatement || e instanceof PsiDoWhileStatement;
  }

  public void unwrap(Editor editor, PsiElement element) throws IncorrectOperationException {
    PsiStatement body = ((PsiLoopStatement)element).getBody();
    extractFromBlockOrSingleStatement(body, element);

    element.delete();
  }
}