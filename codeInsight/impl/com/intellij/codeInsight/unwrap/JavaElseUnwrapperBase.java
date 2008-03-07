package com.intellij.codeInsight.unwrap;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.util.IncorrectOperationException;

import java.util.Set;

public abstract class JavaElseUnwrapperBase extends JavaUnwrapper {
  public JavaElseUnwrapperBase(String description) {
    super(description);
  }

  public boolean isApplicableTo(PsiElement e) {
    return (isElseBlock(e) || isElseKeyword(e)) && isValidConstruct(e);
  }

  private boolean isElseKeyword(PsiElement e) {
    PsiElement p = e.getParent();
    return p instanceof PsiIfStatement && e == ((PsiIfStatement)p).getElseElement();
  }

  private boolean isValidConstruct(PsiElement e) {
    return ((PsiIfStatement)e.getParent()).getElseBranch() != null;
  }

  @Override
  public void collectElementsToIgnore(PsiElement element, Set<PsiElement> result) {
    PsiElement parent = element.getParent();

    while (parent instanceof PsiIfStatement) {
      result.add(parent);
      parent = parent.getParent();
    }
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    PsiStatement elseBranch;

    if (isElseKeyword(element)) {
      elseBranch = ((PsiIfStatement)element.getParent()).getElseBranch();
    }
    else {
      elseBranch = (PsiStatement)element;
    }

    unwrapElseBranch(elseBranch, element.getParent(), context);
  }

  protected abstract void unwrapElseBranch(PsiStatement branch, PsiElement parent, Context context) throws IncorrectOperationException;
}