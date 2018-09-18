
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
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
        final EditorWindow window = e.getData(EditorWindow.DATA_KEY);
        if (window != null){
          final VirtualFile[] files = window.getFiles();
          for (final VirtualFile file : files) {
            window.closeFile(file);
          }
          return;
        }
        FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
        VirtualFile selectedFile = fileEditorManager.getSelectedFiles()[0];
        VirtualFile[] openFiles = fileEditorManager.getSiblings(selectedFile);
        for (final VirtualFile openFile : openFiles) {
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
      presentation.setText(IdeBundle.message("action.close.all.editors.in.tab.group"));
    }
    else {
      presentation.setText(IdeBundle.message("action.close.all.editors"));
    }
    Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(FileEditorManager.getInstance(project).getSelectedFiles().length > 0);
  }
}
