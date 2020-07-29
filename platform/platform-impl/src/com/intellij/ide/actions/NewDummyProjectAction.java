// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

final class NewDummyProjectAction extends AnAction implements DumbAware {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ProjectManagerEx.getInstanceEx().openProject(PathManager.getConfigDir().resolve("dummy.ipr"), OpenProjectTask.newProject());
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    e.getPresentation().setVisible("Platform".equals(System.getProperty(PlatformUtils.PLATFORM_PREFIX_KEY)));
  }
}