
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class PrevSplitAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    final CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(
      project, () -> {
        final FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(project);
        manager.setCurrentWindow(manager.getPrevWindow(manager.getCurrentWindow()));
      }, IdeBundle.message("command.go.to.prev.split"), null
    );
  }

  @Override
  public void update(@NotNull final AnActionEvent event){
    final Project project = event.getProject();
    final Presentation presentation = event.getPresentation();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    final FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(project);
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    presentation.setEnabled (toolWindowManager.isEditorComponentActive() && manager.isInSplitter() && manager.getCurrentWindow() != null);
  }
}
