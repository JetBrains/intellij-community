/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * RefactoringActionHandler is an implementation of IDEA refactoring,
 * with dialogs, UI and all.
 * It is what gets invoked when user choses an item from 'Refactoring' menu.<br>
 *
 * <code>RerfactoringActionHandler</code> is a &quot;one-shot&qout; object: you should not
 * invoke it twice.
 * @see RefactoringActionHandlerFactory
 */
public interface RefactoringActionHandler {
  /**
   * Invokes refactoring action from editor. The refactoring obtains
   * all data from editor selection.
   *
   * @param editor editor that refactoring is invoked in
   * @param file file should correspond to <code>editor</code>
   * @param dataContext can be null.
   */
  void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext);

  /**
   * Invokes refactoring action from elsewhere (not from editor). Some refactorings
   * do not implement this method.
   *
   * @param project
   * @param elements list of elements that refactoring should work on. Refactoring-dependent.
   * @param dataContext can be null.
   */
  void invoke(Project project, PsiElement[] elements, DataContext dataContext);
}