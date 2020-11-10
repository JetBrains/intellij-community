// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface ElevationService {
  static ElevationService getInstance() {
    return ApplicationManager.getApplication().getService(ElevationService.class);
  }

  @NotNull OSProcessHandler createProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException;

  default @NotNull Process createProcess(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    return createProcess(commandLine.toProcessBuilder());
  }

  @NotNull Process createProcess(@NotNull ProcessBuilder processBuilder) throws ExecutionException;
}
