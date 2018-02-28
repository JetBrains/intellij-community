// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
