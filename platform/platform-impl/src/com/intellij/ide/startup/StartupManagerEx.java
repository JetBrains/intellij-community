// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.startup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;

public abstract class StartupManagerEx extends StartupManager {
  public abstract boolean startupActivityPassed();

  public static StartupManagerEx getInstanceEx(Project project) {
    return (StartupManagerEx)getInstance(project);
  }
}
