// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.jshell;

import com.intellij.execution.console.ConsoleHistoryController;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
final class LaunchJShellConsoleAction extends AnAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }

    final VirtualFile contentFile = ConsoleHistoryController.getContentFile(
      JShellRootType.getInstance(),
      JShellRootType.CONTENT_ID,
      ScratchFileService.Option.create_new_always
    );
    assert contentFile != null;
    FileEditorManager.getInstance(project).openFile(contentFile, true);
  }
}
