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

  @Override
  public boolean hasNext() {
    return current!=null;
  }

  @Override
  public PsiElement current() {
    return current;
  }

  @Override
  public void advance() {
    previous = current;
    current = current != null ? current.getNextSibling():null;
  }

  @Override
  public void rewind() {
    current = previous;
    previous = current != null ? current.getPrevSibling():null;
  }

  @Override
  public void reset() {
    previous = current = start;
  }
}
