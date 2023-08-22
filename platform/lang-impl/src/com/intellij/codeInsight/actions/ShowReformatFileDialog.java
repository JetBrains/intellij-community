// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions;

import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ShowReformatFileDialog extends AnAction implements DumbAware {
  private static final @NonNls String HELP_ID = "editing.codeReformatting";

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (project == null || editor == null) {
      presentation.setEnabled(false);
      return;
    }

    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null || file.getVirtualFile() == null) {
      presentation.setEnabled(false);
      return;
    }

    if (LanguageFormatting.INSTANCE.forContext(file) != null) {
      presentation.setEnabled(true);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    DataContext dataContext = event.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (project == null || editor == null) {
      return;
    }

    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null || file.getVirtualFile() == null) {
      return;
    }

    boolean hasSelection = editor.getSelectionModel().hasSelection();
    LayoutCodeDialog dialog = new LayoutCodeDialog(project, file, hasSelection, HELP_ID);
    dialog.show();

    if (dialog.isOK()) {
      new FileInEditorProcessor(file, editor, dialog.getRunOptions()).processCode();
    }
  }
}
