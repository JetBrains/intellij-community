/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.openapi.project.Project;

public abstract class ExecutionManager {
  public static ExecutionManager getInstance(final Project project) {
    return project.getComponent(ExecutionManager.class);
  }

  public abstract RunContentManager getContentManager();

  public abstract void compileAndRun(Runnable startRunnable, RunProfile configuration, RunProfileState state);

  public abstract ProcessHandler[] getRunningProcesses();
}
