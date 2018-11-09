package com.intellij.dupLocator.iterators;

import com.intellij.psi.PsiElement;

/**
 * Iterator that limits processing of specified number of nodes
 */
public final class CountingNodeIterator extends NodeIterator {
  private int index;
  private final int max;
  private final NodeIterator delegate;

  public CountingNodeIterator(int _max, NodeIterator _iterator) {
    max = _max;
    delegate = _iterator;
  }

  @Override
  public boolean hasNext() {
    return index < max && delegate.hasNext();
  }

  @Override
  public PsiElement current() {
    if (index < max)
      return delegate.current();
    return null;
  }

  @Override
  public void advance() {
    ++index;
    delegate.advance();
  }

  @Override
  public void rewind() {
    if (index >0) {
      -- index;
      delegate.rewind();
    }
  }

  @Override
  public void reset() {
    index = 0;
    delegate.reset();
  }
}
