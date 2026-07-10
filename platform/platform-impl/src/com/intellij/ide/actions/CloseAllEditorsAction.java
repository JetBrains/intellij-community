// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public class CloseAllEditorsAction extends AnAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(
      project, () -> {
        FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
        EditorWindow window = e.getData(EditorWindow.DATA_KEY);
        if (window != null){
          fileEditorManager.closeFilesWithChecks(getFilesToClose(window));
          return;
        }
        VirtualFile selectedFile = fileEditorManager.getSelectedFiles()[0];
        List<Pair<EditorComposite, EditorWindow>> filesToClose = new ArrayList<>();
        for (EditorWindow editorWindow : fileEditorManager.getWindows()) {
          filesToClose.addAll(getFilesToClose(editorWindow, fileEditorManager.getSiblings(selectedFile)));
        }
        fileEditorManager.closeFilesWithChecks(filesToClose);
      }, IdeBundle.message("command.close.all.editors"), null
    );
  }

  private static @NotNull List<Pair<EditorComposite, EditorWindow>> getFilesToClose(
    @NotNull EditorWindow window
  ) {
    return getFilesToClose(window, window.getFileList());
  }

  private static @NotNull List<Pair<EditorComposite, EditorWindow>> getFilesToClose(
    @NotNull EditorWindow window,
    @NotNull Iterable<? extends VirtualFile> files
  ) {
    List<Pair<EditorComposite, EditorWindow>> result = new ArrayList<>();
    for (VirtualFile file : files) {
      EditorComposite composite = window.getComposite(file);
      if (composite != null) {
        result.add(new Pair<>(composite, window));
      }
    }
    return result;
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
