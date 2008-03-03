package com.intellij.codeInsight.unwrap;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.editor.Editor;
import com.intellij.codeInsight.CodeInsightBundle;

public class JavaIfUnwrapper extends JavaUnwrapper {
  public JavaIfUnwrapper() {
    super(CodeInsightBundle.message("unwrap.if"));
  }

  public boolean isApplicableTo(PsiElement e) {
    return e instanceof PsiIfStatement && !isElseBlock(e);
  }

  public void unwrap(Editor editor, PsiElement element) throws IncorrectOperationException {
    PsiStatement then = ((PsiIfStatement)element).getThenBranch();
    extractFromBlockOrSingleStatement(then, element);

    element.delete();
  }
}
