// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.startup;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Allows registering activities that are run during project loading. Methods of StartupManager are typically
 * called from {@link com.intellij.openapi.components.ProjectComponent#projectOpened()}.
 */
public abstract class StartupManager {
  /**
   * Returns the startup manager instance for the specified project.
   *
   * @param project the project for which the instance should be returned.
   * @return the startup manager instance.
   */
  public static StartupManager getInstance(Project project) {
    return ServiceManager.getService(project, StartupManager.class);
  }

  public abstract void registerPreStartupActivity(@NotNull Runnable runnable);

  /**
   * Registers an activity that is performed during project load while the "Loading Project"
   * progress bar is displayed. You may NOT access the PSI structures from the activity.
   *
   * @param runnable the activity to execute.
   */
  public abstract void registerStartupActivity(@NotNull Runnable runnable);

  /**
   * Registers an activity that is performed during project load after the "Loading Project"
   * progress bar is displayed. You may access the PSI structures from the activity.
   *
   * @param runnable the activity to execute.
   * @see StartupActivity#POST_STARTUP_ACTIVITY
   */
  public abstract void registerPostStartupActivity(@NotNull Runnable runnable);

  public abstract boolean postStartupActivityPassed();

  /**
   * Executes the specified runnable immediately if invoked from AWT thread and the initialization of the current project
   * is complete; otherwise, registers it as a post-startup activity. In the latter case, the runnable will be executed
   * later on AWT thread in a non-modal state.
   *
   * @param runnable the activity to execute.
   * @see com.intellij.openapi.application.ModalityState
   * @see com.intellij.openapi.application.Application#invokeLater(Runnable)
   */
  public abstract void runWhenProjectIsInitialized(@NotNull Runnable runnable);
}