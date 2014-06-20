package com.intellij.dupLocator.iterators;

import com.intellij.psi.PsiElement;

/**
 * Node iterator interface
 */
public abstract class NodeIterator implements Cloneable {
  public abstract boolean hasNext();
  public abstract PsiElement current();
  public abstract void advance();
  public abstract void rewind();
  public abstract void reset();

  public void rewind(int number) {
    while(number > 0) {
      --number;
      rewind();
    }
  }

  public NodeIterator clone() {
    try {
      return (NodeIterator) super.clone();
    } catch (CloneNotSupportedException e) {
      e.printStackTrace();
      return null;
    }
  }
}
