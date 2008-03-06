package com.intellij.codeInsight.unwrap;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.util.Set;

public abstract class JavaUnwrapper implements Unwrapper {
  private String myDescription;

  public JavaUnwrapper(String description) {
    myDescription = description;
  }

  public abstract boolean isApplicableTo(PsiElement e);

  public void collectElementsToIgnore(PsiElement element, Set<PsiElement> result) {
  }

  public String getDescription(PsiElement e) {
    return myDescription;
  }

  protected boolean isElseBlock(PsiElement e) {
    PsiElement p = e.getParent();
    return p instanceof PsiIfStatement && e == ((PsiIfStatement)p).getElseBranch();
  }

  protected void extractFromBlockOrSingleStatement(PsiStatement block, PsiElement from) throws IncorrectOperationException {
    if (block instanceof PsiBlockStatement) {
      extractFromCodeBlock(((PsiBlockStatement)block).getCodeBlock(), from);
    }
    else if (block != null && !(block instanceof PsiEmptyStatement)) {
      extract(block, block, from);
    }
  }

  protected void extractFromCodeBlock(PsiCodeBlock block, PsiElement from) throws IncorrectOperationException {
    if (block == null) return;
    extract(block.getFirstBodyElement(), block.getLastBodyElement(), from);
  }

  private void extract(PsiElement first, PsiElement last, PsiElement from) throws IncorrectOperationException {
    if (first == null)  return;

    // trim leading empty spaces
    while (first != last && first instanceof PsiWhiteSpace) {
      first = first.getNextSibling();
    }

    // trim trailing empty spaces
    while (last != first && last instanceof PsiWhiteSpace) {
      last = last.getPrevSibling();
    }

    // nothing to extract
    if (first == last && last instanceof PsiWhiteSpace) return;

    from.getParent().addRangeBefore(first, last, from);
  }
}
