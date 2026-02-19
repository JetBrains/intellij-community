// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui.layout.impl;

import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class RunnerLayoutUiFactoryImpl extends RunnerLayoutUi.Factory {
  private final Project myProject;

  public RunnerLayoutUiFactoryImpl(Project project) {
    myProject = project;
    // ensure dockFactory is registered
    RunContentManager.getInstance(project);
  }

  @Override
  public @NotNull RunnerLayoutUi create(final @NotNull String runnerId, final @NotNull String runnerTitle, final @NotNull String sessionName, final @NotNull Disposable parent) {
    return new RunnerLayoutUiImpl(myProject, parent, runnerId, runnerTitle, sessionName);
  }
}
