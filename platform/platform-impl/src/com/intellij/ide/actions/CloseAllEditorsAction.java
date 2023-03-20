// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class CloseAllEditorsAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(
      project, () -> {
        EditorWindow window = e.getData(EditorWindow.DATA_KEY);
        if (window != null){
          for (VirtualFile file : window.getFileList()) {
            window.closeFile(file);
          }
          return;
        }
        FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
        VirtualFile selectedFile = fileEditorManager.getSelectedFiles()[0];
        for (VirtualFile openFile : fileEditorManager.getSiblings(selectedFile)) {
          fileEditorManager.closeFile(openFile);
        }
      }, IdeBundle.message("command.close.all.editors"), null
    );
  }

  @Override
  public void update(@NotNull AnActionEvent event){
    Presentation presentation = event.getPresentation();
    final EditorWindow editorWindow = event.getData(EditorWindow.DATA_KEY);
    if (editorWindow != null && editorWindow.inSplitter()) {
      presentation.setText(IdeBundle.messagePointer("action.close.all.editors.in.tab.group"));
    }
    else {
      presentation.setText(ActionsBundle.messagePointer("action.CloseAllEditors.text"));
    }
    Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(FileEditorManager.getInstance(project).getSelectedFiles().length > 0);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
