/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.openapi.project.Project;

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
    return project.getComponent(StartupManager.class);
  }

  /**
   * Registers an activity which is performed during project load while the "Loading Project"
   * progress bar is displayed. You may NOT access the PSI structures from the activity.
   *
   * @param runnable the activity to execute.
   */
  public abstract void registerStartupActivity(Runnable runnable);

  /**
   * Registers an activity which is performed during project load after the "Loading Project"
   * progress bar is displayed. You may access the PSI structures from the activity.
   *
   * @param runnable the activity to execute.
   */
  public abstract void registerPostStartupActivity(Runnable runnable);

  /**
   * Executes the specified runnable immediately if startup of the current project
   * is complete, otherwise registers it as a post-startup activity.
   * You may access the PSI structures from the activity.
   * 
   * @param runnable the activity to execute.
   */
  public abstract void runPostStartup(Runnable runnable);

  /**
   * Executes the specified runnable immediately if the initialization of the current project
   * is complete, or registers it as a post-startup activity if the project is being initalized.
   *
   * @param runnable the activity to execute.
   */
  public abstract void runWhenProjectIsInitialized(Runnable runnable);

  /**
   * Returns the file system synchronizer instance, which can be used to manage updating
   * data cached by the plugin when the contents of the file system is changed.
   *
   * @return the file system synchronizer instance.
   * @since 6.0
   */
  public abstract FileSystemSynchronizer getFileSystemSynchronizer();
}
