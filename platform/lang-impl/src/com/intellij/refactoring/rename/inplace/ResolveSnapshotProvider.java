package com.intellij.refactoring.rename.inplace;

import com.intellij.psi.PsiElement;

/**
 * User: Maxim.Mossienko
 * Date: 29.07.2009
 * Time: 14:00:17
 */
public abstract class ResolveSnapshotProvider {
  public abstract ResolveSnapshot createSnapshot(PsiElement scope);

  public static abstract class ResolveSnapshot {
    public abstract void apply(String name);
  }
}
