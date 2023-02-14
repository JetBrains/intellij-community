// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import org.jetbrains.annotations.NotNull;

public abstract class StartupManagerEx extends StartupManager {
  public static StartupManagerEx getInstanceEx(@NotNull Project project) {
    return (StartupManagerEx)getInstance(project);
  }

  public abstract boolean startupActivityPassed();
}
