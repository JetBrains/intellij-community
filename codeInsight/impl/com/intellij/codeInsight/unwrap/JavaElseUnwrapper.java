package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.util.IncorrectOperationException;

public class JavaElseUnwrapper extends JavaElseUnwrapperBase {
  public JavaElseUnwrapper() {
    super(CodeInsightBundle.message("unwrap.else"));
  }

  @Override
  protected void unwrapElseBranch(PsiStatement branch, PsiElement parent) throws IncorrectOperationException {
    if (branch instanceof PsiIfStatement) {
      branch = ((PsiIfStatement)branch).getThenBranch();
    }
    
    extractFromBlockOrSingleStatement(branch, parent);
    parent.delete();
  }
}