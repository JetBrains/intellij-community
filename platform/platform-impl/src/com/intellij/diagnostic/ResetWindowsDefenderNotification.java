// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

final class ResetWindowsDefenderNotification extends AnAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(SystemInfo.isWindows);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    WindowsDefenderChecker checker = WindowsDefenderChecker.getInstance();
    checker.ignoreStatusCheck(null, false);
    Project project = e.getProject();
    if (project != null) {
      checker.ignoreStatusCheck(project, false);
      ApplicationManager.getApplication().executeOnPooledThread(
        () -> new WindowsDefenderCheckerActivity().runActivity(project));
    }
  }
}
