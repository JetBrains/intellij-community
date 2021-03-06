package com.intellij.dupLocator.iterators;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public final class SiblingNodeIterator extends NodeIterator {
  private final PsiElement start;
  private PsiElement current;
  private PsiElement previous;

  private SiblingNodeIterator(@NotNull PsiElement element) {
    previous = current = start = element;
  }

  public static NodeIterator create(PsiElement element) {
    return (element == null) ? SingleNodeIterator.EMPTY : new SiblingNodeIterator(element);
  }

  @Override
  public boolean hasNext() {
    return current != null;
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
