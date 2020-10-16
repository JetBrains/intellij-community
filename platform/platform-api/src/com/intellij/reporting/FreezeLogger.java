// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.reporting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class FreezeLogger {

  public static FreezeLogger getInstance() {
    return ApplicationManager.getApplication().getService(FreezeLogger.class);
  }

  public abstract void runUnderPerformanceMonitor(@Nullable Project project, @NotNull Runnable action);

}
