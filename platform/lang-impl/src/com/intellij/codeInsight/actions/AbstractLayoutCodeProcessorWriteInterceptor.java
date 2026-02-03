// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class AbstractLayoutCodeProcessorWriteInterceptor {
  public static AbstractLayoutCodeProcessorWriteInterceptor getInstance() {
    return ApplicationManager.getApplication().getService(AbstractLayoutCodeProcessorWriteInterceptor.class);
  }

  public void runFileWrite(@NotNull Runnable runnable, @NotNull Project project, @NotNull String commandName) {
    runnable.run();
  }
}
