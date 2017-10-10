// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.dashboard.RunDashboardManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;

/**
 * @author Konstantin Aleev
 */
public class PreviousConfigurationAction extends SwitchConfigurationAction {
  public PreviousConfigurationAction() {
    super(ExecutionBundle.message("run.dashboard.previous.configuration.action.name"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    RunDashboardManager.getInstance(project).getDashboardContentManager().selectPreviousContent();
  }
}
