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
  private Project myProject;

  public RefactoringFactoryImpl(final Project project) {
    myProject = project;
  }

  public RenameRefactoring createRename(final PsiElement element, final String newName) {
    return new RenameRefactoringImpl(myProject, element, newName, true, true);
  }

  public SafeDeleteRefactoring createSafeDelete(final PsiElement[] elements) {
    return new SafeDeleteRefactoringImpl(myProject, elements);
  }
}
