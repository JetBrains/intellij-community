// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.SafeDeleteRefactoring;
import org.jetbrains.annotations.NotNull;

final class RefactoringFactoryImpl extends RefactoringFactory {
  private final Project myProject;

  RefactoringFactoryImpl(final Project project) {
    myProject = project;
  }

  @Override
  public RenameRefactoring createRename(final @NotNull PsiElement element, final String newName) {
    return new RenameRefactoringImpl(myProject, element, newName, true, true);
  }

  @Override
  public RenameRefactoring createRename(@NotNull PsiElement element, String newName, boolean searchInComments, boolean searchInNonJavaFiles) {
    return new RenameRefactoringImpl(myProject, element, newName, searchInComments, searchInNonJavaFiles);
  }

  @Override
  public RenameRefactoring createRename(@NotNull PsiElement element,
                                        String newName,
                                        SearchScope scope,
                                        boolean searchInComments, boolean searchInNonJavaFiles) {
    return new RenameRefactoringImpl(myProject, element, newName, scope, searchInComments, searchInNonJavaFiles);
  }

  @Override
  public SafeDeleteRefactoring createSafeDelete(final PsiElement[] elements) {
    return new SafeDeleteRefactoringImpl(myProject, elements);
  }
}
