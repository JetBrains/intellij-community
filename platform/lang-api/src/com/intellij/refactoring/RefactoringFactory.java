// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public abstract class RefactoringFactory {
  public static RefactoringFactory getInstance(Project project) {
    return project.getService(RefactoringFactory.class);
  }

  public abstract RenameRefactoring createRename(@NotNull PsiElement element, String newName);
  public abstract RenameRefactoring createRename(@NotNull PsiElement element, String newName, boolean searchInComments, boolean searchInNonJavaFiles);
  public abstract RenameRefactoring createRename(@NotNull PsiElement element, String newName, SearchScope scope, boolean searchInComments, boolean searchInNonJavaFiles);

  public abstract SafeDeleteRefactoring createSafeDelete(PsiElement[] elements);
}
