package com.intellij.codeInsight.unwrap;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public abstract class JavaUnwrapper implements Unwrapper {
  private String myDescription;

  public JavaUnwrapper(String description) {
    myDescription = description;
  }

  public abstract boolean isApplicableTo(PsiElement e);

  public String getDescription(PsiElement e) {
    return myDescription;
  }

  protected void extractFromBlockOrSingleStatement(PsiStatement block, PsiElement from) throws IncorrectOperationException {
    if (block instanceof PsiBlockStatement) {
      extractFromCodeBlock(((PsiBlockStatement)block).getCodeBlock(), from);
    }
    else if (block != null && !(block instanceof PsiEmptyStatement)) {
      extract(new PsiElement[]{block}, from);
    }
  }

  protected void extractFromCodeBlock(PsiCodeBlock block, PsiElement from) throws IncorrectOperationException {
    if (block == null) return;
    extract(block.getStatements(), from);
  }

  protected void extract(PsiElement[] elements, PsiElement from) throws IncorrectOperationException {
    if (elements.length == 0) return;

    PsiElement first = elements[0];
    PsiElement last = elements[elements.length - 1];
    from.getParent().addRangeBefore(first, last, from);
  }
}
