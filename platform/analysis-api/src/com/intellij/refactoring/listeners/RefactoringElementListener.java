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
package com.intellij.refactoring.listeners;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * {@linkplain RefactoringElementListenerProvider} receives a notification of what happened
 * to element it has been observing during a refactoring.
 *
 * @see com.intellij.refactoring.listeners.RefactoringElementAdapter
 * @see com.intellij.refactoring.listeners.UndoRefactoringElementAdapter
 * @see com.intellij.execution.configurations.RefactoringListenerProvider
 * @author dsl
 */
public interface RefactoringElementListener {
  RefactoringElementListener DEAF = new RefactoringElementListener() {
    @Override
    public void elementMoved(@NotNull PsiElement newElement) {}

    @Override
    public void elementRenamed(@NotNull PsiElement newElement) {}
  };
  
  /**
   * Invoked in write action and command.
   */
  void elementMoved(@NotNull PsiElement newElement);
  /**
   * Invoked in write action and command.
   */
  void elementRenamed(@NotNull PsiElement newElement);
}
