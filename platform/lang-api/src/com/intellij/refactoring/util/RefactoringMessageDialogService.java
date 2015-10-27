package com.intellij.refactoring.util;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

public abstract class RefactoringMessageDialogService {
  public static RefactoringMessageDialogService getInstance() {
    return ServiceManager.getService(RefactoringMessageDialogService.class);
  }

  public abstract RefactoringMessageDialog createDialog(String title, String message, String helpId,
                                                        String iconId, boolean showCancelButton, Project project);
}
