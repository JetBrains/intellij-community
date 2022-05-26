// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenameRefactoringDialog;
import com.intellij.ui.ReplacePromptDialog;

public class RefactoringUiServiceImpl extends RefactoringUiService {

  @Override
  public RenameRefactoringDialog createRenameRefactoringDialog(Project project,
                                                               PsiElement element,
                                                               PsiElement context,
                                                               Editor editor) {
    return new RenameDialog(project, element, context, editor);
  }

  @Override
  public int showReplacePromptDialog(boolean isMultipleFiles, @NlsContexts.DialogTitle String title, Project project) {
    ReplacePromptDialog promptDialog = new ReplacePromptDialog(isMultipleFiles, title, project);
    promptDialog.show();
    return promptDialog.getExitCode();
  }
}
