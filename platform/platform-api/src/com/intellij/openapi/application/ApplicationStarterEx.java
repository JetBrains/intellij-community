// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.ide.CliResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Future;

/** @deprecated override {@link ApplicationStarter} instead */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
public abstract class ApplicationStarterEx implements ApplicationStarter {
  @NotNull
  @Override
  public final Future<? extends CliResult> processExternalCommandLineAsync(@NotNull String[] args, @Nullable String currentDirectory) {
    processExternalCommandLine(args, currentDirectory);
    return CliResult.ok();
  }

  /**
   * @deprecated use async version {@link #processExternalCommandLineAsync}
   */
  @SuppressWarnings("unused")
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  @Deprecated
  public void processExternalCommandLine(@NotNull String[] args, @Nullable String currentDirectory) {
    throw new UnsupportedOperationException("Class " + getClass().getName() + " must implement `processExternalCommandLine()`");
  }
}