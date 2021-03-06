// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

public class ResetWindowsDefenderNotification extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    PropertiesComponent.getInstance().setValue(WindowsDefenderChecker.IGNORE_VIRUS_CHECK, false);
    Project project = e.getProject();
    if (project != null) {
      PropertiesComponent.getInstance(project).setValue(WindowsDefenderChecker.IGNORE_VIRUS_CHECK, false);
      ApplicationManager.getApplication().executeOnPooledThread(
        () -> new WindowsDefenderCheckerActivity().runActivity(project));
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(SystemInfo.isWindows);
  }
}
