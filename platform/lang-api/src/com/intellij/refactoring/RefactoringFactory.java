/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.refactoring;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public abstract class RefactoringFactory {
  public static RefactoringFactory getInstance(Project project) {
    return ServiceManager.getService(project, RefactoringFactory.class);
  }

  public abstract RenameRefactoring createRename(@NotNull PsiElement element, String newName);
  public abstract RenameRefactoring createRename(@NotNull PsiElement element, String newName, boolean searchInComments, boolean searchInNonJavaFiles);

  public abstract SafeDeleteRefactoring createSafeDelete(PsiElement[] elements);
}
