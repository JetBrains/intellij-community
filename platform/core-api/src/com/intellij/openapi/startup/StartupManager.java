// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.startup;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Allows registering activities that are run during project loading.
 *
 * @see StartupActivity
 */
public abstract class StartupManager {
  /**
   * Returns the startup manager instance for the specified project.
   *
   * @param project the project for which the instance should be returned.
   * @return the startup manager instance.
   */
  public static StartupManager getInstance(Project project) {
    return project.getService(StartupManager.class);
  }

  /**
   * @deprecated Use {@link #registerStartupActivity(Runnable)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public void registerPreStartupActivity(@NotNull Runnable runnable) {
    registerStartupActivity(runnable);
  }

  /**
   * Registers an activity that is performed during project load while the "Loading Project"
   * progress bar is displayed. You may NOT access the PSI structures from the activity.
   */
  @ApiStatus.Internal
  public abstract void registerStartupActivity(@NotNull Runnable runnable);

  /**
   * Registers an activity that is performed after project is opened without any visible progress.
   * You may access the PSI structures from the activity, unless runnable implements {@link DumbAware}.</p>
   * <p>
   * Consider to use {@link #runAfterOpened} if possible.
   *
   * @param runnable the activity to execute.
   * @see StartupActivity#POST_STARTUP_ACTIVITY
   */
  public abstract void registerPostStartupActivity(@NotNull Runnable runnable);

  /**
   * Registers activity that is executed on pooled thread after project is opened.
   * The runnable will be executed in current thread if project is already opened.</p>
   * <p>
   * See <a href="https://github.com/JetBrains/intellij-community/blob/master/platform/service-container/overview.md#startup-activity">docs</a> for details.
   *
   * @see StartupActivity#POST_STARTUP_ACTIVITY
   */
  public abstract void runAfterOpened(@NotNull Runnable runnable);

  public abstract boolean postStartupActivityPassed();

  /**
   * Registers activity that is executed after project is opened.
   * If runnable implements {@link DumbAware}, it will be executed on EDT thread in a non-modal state.
   * Otherwise, it will be executed on EDT when indexes are ready.
   * <p>
   * The runnable can be executed immediately if method is called from EDT and project is already opened.
   * <p>
   * Consider to use {@link #runAfterOpened} if possible.
   *
   * @param runnable the activity to execute.
   * @see com.intellij.openapi.application.ModalityState
   * @see com.intellij.openapi.application.Application#invokeLater(Runnable)
   */
  public abstract void runWhenProjectIsInitialized(@NotNull Runnable runnable);
}