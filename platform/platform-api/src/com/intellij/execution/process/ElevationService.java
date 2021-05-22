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

  /**
   * This method can be used to ensure the service has an active authorization by the time you ask it to elevate a process,
   * so that creating the process is performed without an authorization prompt.
   * This is useful when launching the process involves additional non-trivial steps (like launching an external terminal
   * emulator application), that you would like to avoid performing in case the user decides to cancel the authorization
   * (or fails the authentication).
   *
   * Note that this approach is inherently racy, and should only be treated as the best effort to improve UX.
   * Successful completion of the authorization using this method does not guarantee that the consequent call to
   * one of the process creation methods succeeds.
   * Your code must always be ready that the process creation methods may always request an additional authorization,
   * even after going through it using this method.
   */
  void authorizeService() throws ExecutionException;

  @NotNull OSProcessHandler createProcessHandler(@NotNull GeneralCommandLine commandLine) throws ExecutionException;

  default @NotNull Process createProcess(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    return createProcess(commandLine.toProcessBuilder());
  }

  @NotNull Process createProcess(@NotNull ProcessBuilder processBuilder) throws ExecutionException;
}
