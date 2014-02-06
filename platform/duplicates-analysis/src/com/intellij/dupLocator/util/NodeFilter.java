package com.intellij.dupLocator.util;

import com.intellij.psi.PsiElement;

/**
 * Base class for tree filtering
 */
public interface NodeFilter {
  boolean accepts(PsiElement element);
}
