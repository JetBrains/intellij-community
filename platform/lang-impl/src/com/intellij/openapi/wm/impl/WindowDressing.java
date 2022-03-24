// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.lightEdit.LightEditServiceListener;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;

public final class WindowDressing implements ProjectManagerListener, LightEditServiceListener {
  @Override
  public void projectOpened(@NotNull Project project) {
    getWindowActionGroup().addProject(project);
  }

  @Override
  public void projectClosed(@NotNull Project project) {
    getWindowActionGroup().removeProject(project);
  }

  @NotNull
  public static ProjectWindowActionGroup getWindowActionGroup() {
    return (ProjectWindowActionGroup)ActionManager.getInstance().getAction("OpenProjectWindows");
  }

  @Override
  public void lightEditWindowOpened(@NotNull Project project) {
    getWindowActionGroup().addProject(project);
  }

  @Override
  public void lightEditWindowClosed(@NotNull Project project) {
    getWindowActionGroup().removeProject(project);
  }
}
