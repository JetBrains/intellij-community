package com.intellij.dupLocator.iterators;

import com.intellij.psi.PsiElement;

/**
 * Node iterator over array
 */
public final class ArrayBackedNodeIterator extends NodeIterator {
  private final PsiElement[] nodes;
  private int index;

  public ArrayBackedNodeIterator(final PsiElement[] _nodes) {
    nodes = _nodes;
    index = 0;
  }

  @Override
  public boolean hasNext() {
    return index < nodes.length;
  }

  @Override
  public void rewind(int number) {
    index -= number;
  }

  @Override
  public PsiElement current() {
    if (index < nodes.length)
      return nodes[index];

    return null;
  }

  @Override
  public void advance() {
    ++index;
  }

  @Override
  public void rewind() {
    --index;
  }

  @Override
  public void reset() {
    index = 0;
  }
}
