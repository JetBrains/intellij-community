package com.intellij.codeInsight.unwrap;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public abstract class JavaElseUnwrapperBase extends JavaUnwrapper {
  public JavaElseUnwrapperBase(String description) {
    super(description);
  }

  public boolean isApplicableTo(PsiElement e) {
    return isElseBlock(e) || isElseKeyword(e);
  }

  private boolean isElseKeyword(PsiElement e) {
    PsiElement p = e.getParent();
    return p instanceof PsiIfStatement && e == ((PsiIfStatement)p).getElseElement();
  }

  public void unwrap(Editor editor, PsiElement element) throws IncorrectOperationException {
    PsiStatement elseBranch;

    if (isElseKeyword(element)) {
      elseBranch = ((PsiIfStatement)element.getParent()).getElseBranch();
      if (elseBranch == null) return;
    }
    else {
      elseBranch = (PsiStatement)element;
    }

    unwrapElseBranch(elseBranch, element.getParent());
  }

  protected abstract void unwrapElseBranch(PsiStatement branch, PsiElement parent) throws IncorrectOperationException;
}