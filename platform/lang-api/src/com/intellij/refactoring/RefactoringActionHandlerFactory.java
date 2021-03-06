// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.openapi.application.ApplicationManager;

/**
 * Creates {@link RefactoringActionHandler}s for various refactorings.
 *
 * @author dsl
 */
public abstract class RefactoringActionHandlerFactory {

  public static RefactoringActionHandlerFactory getInstance() {
    return ApplicationManager.getApplication().getService(RefactoringActionHandlerFactory.class);
  }

  /**
   * Creates handler for Safe Delete refactoring.
   * <p>
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts a list of {@link com.intellij.psi.PsiElement}s to delete.
   */
  public abstract RefactoringActionHandler createSafeDeleteHandler();

  public abstract RefactoringActionHandler createMoveHandler();

  public abstract RefactoringActionHandler createRenameHandler();
}
