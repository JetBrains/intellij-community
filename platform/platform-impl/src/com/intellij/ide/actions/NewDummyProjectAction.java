// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class NewDummyProjectAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    Project project = projectManager.newProject("dummy", PathManager.getConfigPath() + "/dummy.ipr", true, false);
    if (project == null) return;
    projectManager.openProject(project);
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    e.getPresentation().setVisible("Platform".equals(System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY)));
  }
}