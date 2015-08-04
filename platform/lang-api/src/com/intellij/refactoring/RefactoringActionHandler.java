/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring;

import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * RefactoringActionHandler is an implementation of IDEA refactoring,
 * with dialogs, UI and all.
 * It is what gets invoked when user chooses an item from 'Refactoring' menu.<br>
 *
 * <code>RefactoringActionHandler</code> is a &quot;one-shot&quot; object: you should not
 * invoke it twice.
 *
 * @see RefactoringActionHandlerFactory
 * @see ContextAwareActionHandler to hide an action from popups but allow access by shortcut, main menu or find.
 */
public interface RefactoringActionHandler {
  /**
   * Invokes refactoring action from editor. The refactoring obtains
   * all data from editor selection.
   *
   * @param project     the project in which the refactoring is invoked.
   * @param editor      editor that refactoring is invoked in
   * @param file        file should correspond to <code>editor</code>
   * @param dataContext can be null for some but not all of refactoring action handlers
   *                    (it is recommended to pass DataManager.getDataContext() instead of null)
   */
  void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext);

  /**
   * Invokes refactoring action from elsewhere (not from editor). Some refactorings
   * do not implement this method.
   *
   * @param project     the project in which the refactoring is invoked.
   * @param elements    list of elements that refactoring should work on. Refactoring-dependent.
   * @param dataContext can be null for some but not all of refactoring action handlers
   *                    (it is recommended to pass DataManager.getDataContext() instead of null)
   */
  void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext);
}