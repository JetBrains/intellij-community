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

  public boolean hasNext() {
    return index < nodes.length;
  }

  public void rewind(int number) {
    index -= number;
  }

  public PsiElement current() {
    if (index < nodes.length)
      return nodes[index];

    return null;
  }

  public void advance() {
    ++index;
  }

  public void rewind() {
    --index;
  }

  public void reset() {
    index = 0;
  }
}
