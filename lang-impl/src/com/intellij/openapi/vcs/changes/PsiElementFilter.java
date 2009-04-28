package com.intellij.openapi.vcs.changes;

import com.intellij.psi.PsiElement;

/**
 * @author Konstantin Bulenkov
 */
public class PsiElementFilter<T extends PsiElement> {
  private final Class<T> filter;

  public PsiElementFilter(Class<T> filter) {
    this.filter = filter;
  }

  public boolean accept(T element) {
    return true;
  }

  public final Class<T> getClassFilter() {
    return filter; 
  }
}
