/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.listeners;

import com.intellij.psi.PsiElement;

/**
 * {@linkplain RefactoringElementListenerProvider} recieves a notifictaion of what happened
 * to element it have been observing during a refactoring.
 * @author dsl
 */
public interface RefactoringElementListener {
  /**
   * Invoked in write action and command.
   */
  void elementMoved(PsiElement newElement);
  /**
   * Invoked in write action and command.
   */
  void elementRenamed(PsiElement newElement);
}
