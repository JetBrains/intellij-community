/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.startup;

import com.intellij.openapi.project.Project;

public abstract class StartupManager {
  public static StartupManager getInstance(Project project) {
    return project.getComponent(StartupManager.class);
  }

  public abstract void registerStartupActivity(Runnable runnable);

  public abstract void registerPostStartupActivity(Runnable runnable);

  public abstract void runWhenProjectIsInitialized(Runnable runnable);
}
