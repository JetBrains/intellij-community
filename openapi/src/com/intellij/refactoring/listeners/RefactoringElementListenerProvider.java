/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.listeners;

import com.intellij.psi.PsiElement;

/**
 * Refactorings invoke {@link #getListener(com.intellij.psi.PsiElement)} of registered
 * {@linkplain RefactoringElementListenerProvider} before particular element is subjected to refactoring.
 * @author dsl
 */
public interface RefactoringElementListenerProvider {
  /**
   *
   * Should return a listener for particular element. Invoked in read action.
   */
  RefactoringElementListener getListener(PsiElement element);
}
