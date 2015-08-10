/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.startup;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to register activities which are run during project loading. Methods of StartupManager are typically
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
   * Registers an activity which is performed during project load while the "Loading Project"
   * progress bar is displayed. You may NOT access the PSI structures from the activity.
   *
   * @param runnable the activity to execute.
   */
  public abstract void registerStartupActivity(@NotNull Runnable runnable);

  /**
   * Registers an activity which is performed during project load after the "Loading Project"
   * progress bar is displayed. You may access the PSI structures from the activity.
   *
   * @param runnable the activity to execute.
   * @see StartupActivity#POST_STARTUP_ACTIVITY
   */
  public abstract void registerPostStartupActivity(@NotNull Runnable runnable);

  /**
   * Executes the specified runnable as soon as possible if the initialization of the current project
   * is complete, or registers it as a post-startup activity if the project is being initialized.<p/>
   *
   * The runnnable is executed on AWT thread in a non-modal state.
   *
   * @param runnable the activity to execute.
   * @see com.intellij.openapi.application.ModalityState
   * @see com.intellij.openapi.application.Application#invokeLater(Runnable)
   */
  public abstract void runWhenProjectIsInitialized(@NotNull Runnable runnable);
}
