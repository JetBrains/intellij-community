/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.listeners;

import com.intellij.openapi.project.Project;

/**
 * This class managers <i>refactoring listeners</i> - a way for plugin/client code to get
 * notifications that particular refactoring has done something with some piece of Java code in
 * a project.<p>
 *
 * Listening to refactoring operations works as follows:
 * <ul>
 * <li> client wishing to recieve notifications registers a {@link RefactoringElementListenerProvider}
 *  with this class.
 * <li> before some <code>PsiElement</code> is subjected to a refactoring, all registered providers
 *  are asked to provide a {@link RefactoringElementListener} for that element
 * ({@link RefactoringElementListenerProvider#getListener(com.intellij.psi.PsiElement)} is invoked)
 * <li>When refactoring is completed, listeners for all refactoring subjects are notified,
 * </ul>
 */
public abstract class RefactoringListenerManager {
  /**
   * Registers a provider of listeners.
   */
  public abstract void addListenerProvider(RefactoringElementListenerProvider provider);

  /**
   * Unregisters previously registered provider of listeners.   
   */
  public abstract void removeListenerProvider(RefactoringElementListenerProvider provider);

  public static RefactoringListenerManager getInstance(Project project) {
    return project.getComponent(RefactoringListenerManager.class);
  }
}
