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

package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.SafeDeleteRefactoring;

/**
 * @author yole
 */
public class RefactoringFactoryImpl extends RefactoringFactory {
  private final Project myProject;

  public RefactoringFactoryImpl(final Project project) {
    myProject = project;
  }

  @Override
  public RenameRefactoring createRename(final PsiElement element, final String newName) {
    return new RenameRefactoringImpl(myProject, element, newName, true, true);
  }

  @Override
  public RenameRefactoring createRename(PsiElement element, String newName, boolean searchInComments, boolean searchInNonJavaFiles) {
    return new RenameRefactoringImpl(myProject, element, newName, searchInComments, searchInNonJavaFiles);
  }

  @Override
  public SafeDeleteRefactoring createSafeDelete(final PsiElement[] elements) {
    return new SafeDeleteRefactoringImpl(myProject, elements);
  }
}
