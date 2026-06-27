// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public class CloseAllEditorsButActiveAction extends AnAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    VirtualFile selectedFile;
    final EditorWindow window = e.getData(EditorWindow.DATA_KEY);
    if (window != null) {
      closeAllExcept(fileEditorManager, window, e.getData(CommonDataKeys.VIRTUAL_FILE));
      return;
    }
    selectedFile = fileEditorManager.getSelectedFiles()[0];
    List<Pair<EditorComposite, EditorWindow>> filesToClose = new ArrayList<>();
    for (EditorWindow editorWindow : fileEditorManager.getWindows()) {
      for (final VirtualFile sibling : fileEditorManager.getSiblings(selectedFile)) {
        if (!Comparing.equal(selectedFile, sibling)) {
          EditorComposite composite = editorWindow.getComposite(sibling);
          if (composite != null) {
            filesToClose.add(new Pair<>(composite, editorWindow));
          }
        }
      }
    }
    fileEditorManager.closeFilesWithChecks(filesToClose);
  }

  static void closeAllExcept(@NotNull FileEditorManagerEx fileEditorManager,
                             @NotNull EditorWindow window,
                             VirtualFile selectedFile) {
    List<Pair<EditorComposite, EditorWindow>> filesToClose = new ArrayList<>();
    for (VirtualFile file : window.getFileList()) {
      if (!Comparing.equal(file, selectedFile) && !window.isFilePinned(file)) {
        EditorComposite composite = window.getComposite(file);
        if (composite != null) {
          filesToClose.add(new Pair<>(composite, window));
        }
      }
    }
    fileEditorManager.closeFilesWithChecks(filesToClose);
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
