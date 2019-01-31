// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.startup;

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

  @Override
  public abstract void registerPreStartupActivity(@NotNull Runnable runnable); // should be used only to register to FileSystemSynchronizer!

  public static StartupManagerEx getInstanceEx(Project project) {
    return (StartupManagerEx)getInstance(project);
  }
}
