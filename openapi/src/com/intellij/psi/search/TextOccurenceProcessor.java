package com.intellij.psi.search;

import com.intellij.psi.PsiElement;

/**
 * @author ven
 */
public interface TextOccurenceProcessor {
  boolean execute (PsiElement element, int offsetInElement);
}
