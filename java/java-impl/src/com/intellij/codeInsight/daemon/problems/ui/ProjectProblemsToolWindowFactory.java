// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NotNull;

public class ProjectProblemsToolWindowFactory implements ToolWindowFactory {

  @Override
  public boolean shouldBeAvailable(@NotNull Project project) {
    return Registry.is("project.problems.view") || ApplicationManager.getApplication().isUnitTestMode();
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    toolWindow.setAvailable(true, null);
    toolWindow.setTitle(ToolWindowId.PROJECT_PROBLEMS_VIEW);
    ProjectProblemsView problemsView = ProjectProblemsView.SERVICE.INSTANCE.getInstance(project);
    problemsView.init(toolWindow);
  }
}