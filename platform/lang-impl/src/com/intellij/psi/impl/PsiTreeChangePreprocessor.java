package com.intellij.psi.impl;

/**
 * @author yole
 */
public interface PsiTreeChangePreprocessor {
  void treeChanged(PsiTreeChangeEventImpl event);
}
