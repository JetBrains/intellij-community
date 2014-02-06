package com.intellij.dupLocator.iterators;

import com.intellij.psi.PsiElement;

/**
 * Iterates over siblings
 */
public final class SiblingNodeIterator extends NodeIterator {
  private final PsiElement start;
  private PsiElement current;
  private PsiElement previous;

  public SiblingNodeIterator(final PsiElement element) {
    previous = current = start = element;
  }

  public boolean hasNext() {
    return current!=null;
  }

  public PsiElement current() {
    return current;
  }

  public void advance() {
    previous = current;
    current = current != null ? current.getNextSibling():null;
  }

  public void rewind() {
    current = previous;
    previous = current != null ? current.getPrevSibling():null;
  }

  public void reset() {
    previous = current = start;
  }
}
