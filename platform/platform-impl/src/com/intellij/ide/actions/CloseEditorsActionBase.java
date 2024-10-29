// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FileStatusManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class CloseEditorsActionBase extends AnAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  protected List<Pair<EditorComposite, EditorWindow>> getFilesToClose(@NotNull AnActionEvent event) {
    List<Pair<EditorComposite, EditorWindow>> result = new ArrayList<>();
    DataContext dataContext = event.getDataContext();
    Project project = event.getRequiredData(CommonDataKeys.PROJECT);
    FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    EditorWindow editorWindow = EditorWindow.DATA_KEY.getData(dataContext);
    EditorWindow[] windows = editorWindow == null ? fileEditorManager.getWindows() : new EditorWindow[]{editorWindow};
    FileStatusManager fileStatusManager = FileStatusManager.getInstance(project);
    if (fileStatusManager != null) {
      for (int i = 0; i != windows.length; ++i) {
        EditorWindow window = windows[i];
        for (EditorComposite composite : window.getAllComposites()) {
          if (isFileToClose(composite, window, fileEditorManager) || isFileToCloseInContext(event.getDataContext(), composite, window)) {
            result.add(new Pair<>(composite, window));
          }
        }
      }
    }
    return result;
  }

  protected abstract boolean isFileToClose(@NotNull EditorComposite editor,
                                           @NotNull EditorWindow window,
                                           @NotNull FileEditorManagerEx fileEditorManager);

  protected boolean isFileToCloseInContext(DataContext dataContext, EditorComposite editor, EditorWindow window) {
    return false;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(
      project, () -> {
        List<Pair<EditorComposite, EditorWindow>> filesToClose = getFilesToClose(e);
        for (int i = 0; i != filesToClose.size(); ++i) {
          final Pair<EditorComposite, EditorWindow> we = filesToClose.get(i);
          we.getSecond().closeFile(we.getFirst().getFile());
        }
      }, IdeBundle.message("command.close.all.unmodified.editors"), null
    );
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    EditorWindow editorWindow = EditorWindow.DATA_KEY.getData(dataContext);
    boolean inSplitter = editorWindow != null && editorWindow.inSplitter();
    presentation.setText(getPresentationText(inSplitter));
    Project project = event.getData(CommonDataKeys.PROJECT);
    boolean enabled = (project != null && isActionEnabled(project, event));
    presentation.setEnabled(enabled);
    if (event.isFromContextMenu()) {
      presentation.setVisible(enabled);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  protected boolean isActionEnabled(Project project, AnActionEvent event) {
    return !getFilesToClose(event).isEmpty();
  }

  protected abstract @NlsContexts.Command String getPresentationText(boolean inSplitter);
}
