package com.intellij.lang.folding;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 1, 2005
 * Time: 12:14:17 AM
 * To change this template use File | Settings | File Templates.
 */
public class FoldingDescriptor {
  private PsiElement myElement;
  private TextRange myRange;

  public FoldingDescriptor(final PsiElement element, final TextRange range) {
    myElement = element;
    myRange = range;
  }

  public PsiElement getElement() {
    return myElement;
  }

  public TextRange getRange() {
    return myRange;
  }
}
