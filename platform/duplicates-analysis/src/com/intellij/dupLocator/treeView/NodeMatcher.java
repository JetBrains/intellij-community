package com.intellij.dupLocator.treeView;

import com.intellij.psi.PsiElement;

public interface NodeMatcher {
  boolean match(PsiElement node);
}
