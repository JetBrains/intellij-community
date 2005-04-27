package com.intellij.lang.refactoring;

import com.intellij.psi.PsiElement;

/**
 * @author ven
 */
public interface RefactoringSupportProvider {
  boolean isSafeDeleteAvailable (PsiElement element);
}
