package com.intellij.refactoring.util;

import com.intellij.openapi.project.Project;

public class RefactoringMessageDialogServiceImpl extends RefactoringMessageDialogService {
  @Override
  public RefactoringMessageDialog createDialog(String title, String message, String helpId,
                                               String iconId, boolean showCancelButton, Project project) {
    return new RefactoringMessageDialog(title, message, helpId, iconId, showCancelButton, project);
  }
}
