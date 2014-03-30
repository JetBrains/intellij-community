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
package com.intellij.ide.startup;

import com.intellij.ide.caches.CacheUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public abstract class StartupManagerEx extends StartupManager {
  public abstract boolean startupActivityRunning();
  public abstract boolean startupActivityPassed();

  public abstract boolean postStartupActivityPassed();

  public abstract void registerPreStartupActivity(@NotNull Runnable runnable); // should be used only to register to FileSystemSynchronizer!

  /**
   * Registers a CacheUpdater instance that will be used to build initial caches and indices.
   * Must be called in registerPreStartupActivity or registerStartupActivity
   * @param updater to be run
   */
  public abstract void registerCacheUpdater(@NotNull CacheUpdater updater);

  public static StartupManagerEx getInstanceEx(Project project) {
    return (StartupManagerEx)getInstance(project);
  }
}
