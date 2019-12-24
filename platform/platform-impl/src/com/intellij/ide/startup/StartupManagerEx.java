// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
