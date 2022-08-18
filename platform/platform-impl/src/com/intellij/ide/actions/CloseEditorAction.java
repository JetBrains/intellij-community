
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class CloseEditorAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);

    FileEditorManagerEx editorManager = getEditorManager(project);
    EditorWindow window = e.getData(EditorWindow.DATA_KEY);
    VirtualFile file = null;
    if (window == null) {
      window = editorManager.getCurrentWindow();
      if (window != null) {
        file = window.getSelectedFile();
      }
    }
    else {
      file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    }
    if (file != null) {
      editorManager.closeFile(file, window);
    }
  }

  private static FileEditorManagerEx getEditorManager(Project project) {
    return (FileEditorManagerEx)FileEditorManager.getInstance(project);
  }

  @Override
  public void update(@NotNull final AnActionEvent event){
    final Presentation presentation = event.getPresentation();
    final Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    EditorWindow window = event.getData(EditorWindow.DATA_KEY);
    if (window == null) {
      window = getEditorManager(project).getCurrentWindow();
    }
    presentation.setEnabled(window != null && window.getTabCount() > 0);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
