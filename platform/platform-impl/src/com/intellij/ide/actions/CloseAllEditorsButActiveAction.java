// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class CloseAllEditorsButActiveAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    FileEditorManagerEx fileEditorManager=FileEditorManagerEx.getInstanceEx(project);
    VirtualFile selectedFile;
    final EditorWindow window = e.getData(EditorWindow.DATA_KEY);
    if (window != null){
      window.closeAllExcept(e.getData(CommonDataKeys.VIRTUAL_FILE));
      return;
    }
    selectedFile = fileEditorManager.getSelectedFiles()[0];
    final VirtualFile[] siblings = fileEditorManager.getSiblings(selectedFile);
    for (final VirtualFile sibling : siblings) {
      if (!Comparing.equal(selectedFile, sibling)) {
        fileEditorManager.closeFile(sibling);
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    VirtualFile selectedFile;
    final EditorWindow window = event.getData(EditorWindow.DATA_KEY);
    if (window != null){
      presentation.setEnabled(window.getFiles().length > 1);
      return;
    } else {
      if (fileEditorManager.getSelectedFiles().length == 0) {
        presentation.setEnabled(false);
        return;
      }
      selectedFile = fileEditorManager.getSelectedFiles()[0];
    }
    VirtualFile[] siblings = fileEditorManager.getSiblings(selectedFile);
    presentation.setEnabled(siblings.length > 1);
  }
}
