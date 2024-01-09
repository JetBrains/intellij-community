// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class CloseAllEditorsButActiveAction extends AnAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    VirtualFile selectedFile;
    final EditorWindow window = e.getData(EditorWindow.DATA_KEY);
    if (window != null) {
      window.closeAllExcept(e.getData(CommonDataKeys.VIRTUAL_FILE));
      return;
    }
    selectedFile = fileEditorManager.getSelectedFiles()[0];
    for (final VirtualFile sibling : fileEditorManager.getSiblings(selectedFile)) {
      if (!Comparing.equal(selectedFile, sibling)) {
        fileEditorManager.closeFile(sibling);
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    VirtualFile selectedFile;
    final EditorWindow window = event.getData(EditorWindow.DATA_KEY);
    if (window != null) {
      presentation.setEnabled(window.getFileList().size() > 1);
      return;
    }
    else {
      if (fileEditorManager.getSelectedFiles().length == 0) {
        presentation.setEnabled(false);
        return;
      }
      selectedFile = fileEditorManager.getSelectedFiles()[0];
    }
    presentation.setEnabled(!ApplicationManager.getApplication().isUnitTestMode()
                            && fileEditorManager.getSiblings(selectedFile).size() > 1);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
