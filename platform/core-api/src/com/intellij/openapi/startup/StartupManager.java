// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.startup;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Allows registering activities that are run during project loading.
 *
 * @see ProjectActivity
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-components.html#project-open">IntelliJ Platform Docs</a>
 */
public abstract class StartupManager {
  /**
   * @return Startup manager instance for the specified project.
   */
  public static StartupManager getInstance(@NotNull Project project) {
    return project.getService(StartupManager.class);
  }

  /**
   * Registers an activity that is performed during the project load while the "Loading Project"
   * progress bar is displayed.
   * You may NOT access PSI from this activity.
   */
  @ApiStatus.Internal
  public abstract void registerStartupActivity(@NotNull Runnable runnable);

  /**
   * @deprecated Consider using extension point {@link ProjectActivity} instead.
   */
  @Deprecated
  public abstract void registerPostStartupActivity(@NotNull Runnable runnable);

  /**
   * Registers activity that is executed on pooled thread after the project is opened.
   * The runnable will be executed in the current thread if the project is already opened.
   *
   * @implNote Consider using extension point {@link ProjectActivity} instead.
   */
  @ApiStatus.Internal
  public abstract void runAfterOpened(@NotNull Runnable runnable);

  public abstract boolean postStartupActivityPassed();

  /**
   * Registers activity that is executed after the project is opened.
   * If runnable implements {@link DumbAware}, it will be executed on EDT thread in a non-modal state.
   * Otherwise, it will be executed on EDT when indexes are ready.
   * <p>
   * The runnable can be executed immediately if the method is called from EDT and the project is already opened.
   *
   * @deprecated Consider using extension point {@link ProjectActivity} instead.
   */
  @Deprecated
  public abstract void runWhenProjectIsInitialized(@NotNull Runnable runnable);

  @ApiStatus.Internal
  public abstract @NotNull Job getAllActivitiesPassedFuture();
}