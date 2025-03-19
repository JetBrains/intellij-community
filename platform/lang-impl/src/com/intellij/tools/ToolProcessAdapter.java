// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;

public final class ToolProcessAdapter extends ProcessAdapter {
  private final Project myProject;
  private final boolean mySynchronizeAfterExecution;
  private final String myName;

  public ToolProcessAdapter(@NotNull Project project, boolean synchronizeAfterExecution, @NotNull String name) {
    myProject = project;
    mySynchronizeAfterExecution = synchronizeAfterExecution;
    myName = name;
  }

  @Override
  public void processTerminated(@NotNull ProcessEvent event) {
    if (ProjectManagerEx.getInstanceEx().isProjectOpened(myProject)) {
      var statusBar = WindowManager.getInstance().getStatusBar(myProject);
      if (statusBar != null) {
        statusBar.setInfo(ToolsBundle.message("tools.completed.message", myName, event.getExitCode()));
      }
    }

    if (mySynchronizeAfterExecution) {
      SaveAndSyncHandler.getInstance().scheduleRefresh();
    }
  }
}
