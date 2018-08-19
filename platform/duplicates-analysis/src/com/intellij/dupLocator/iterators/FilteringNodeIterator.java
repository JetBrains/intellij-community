package com.intellij.dupLocator.iterators;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.PsiElement;

/**
 * Iterator over important nodes
 */
public class FilteringNodeIterator extends NodeIterator {
  private final NodeIterator delegate;
  private final NodeFilter filter;

  private void advanceToNext() {
    while (delegate.hasNext() && filter.accepts(delegate.current())) {
      delegate.advance();
    }
  }

  private void rewindToPrevious() {
    while (filter.accepts(delegate.current())) {
      delegate.rewind();
    }
  }

  public FilteringNodeIterator(NodeIterator iterator, NodeFilter filter) {
    delegate = iterator;
    this.filter = filter;
    advanceToNext();
  }

  @Override
  public boolean hasNext() {
    return delegate.hasNext() && !filter.accepts(delegate.current());
  }

  @Override
  public void rewind(int number) {
    while(number > 0) {
      delegate.rewind();
      rewindToPrevious();
      --number;
    }
  }

  @Override
  public PsiElement current() {
    return delegate.current();
  }

  @Override
  public void advance() {
    delegate.advance();
    advanceToNext();
  }

  @Override
  public void rewind() {
    delegate.rewind();
    rewindToPrevious();
  }

  @Override
  public void reset() {
    delegate.reset();
    advanceToNext();
  }
}
