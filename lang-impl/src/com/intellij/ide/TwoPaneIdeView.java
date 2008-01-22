package com.intellij.ide;

import com.intellij.psi.PsiElement;

/**
 * @author yole
 */
public interface TwoPaneIdeView {
  void selectElement(PsiElement element, boolean selectInActivePanel);
}