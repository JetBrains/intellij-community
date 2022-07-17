// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.ide.CliResult;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/** @deprecated override {@link ApplicationStarter} instead */
@Deprecated(forRemoval = true)
public abstract class ApplicationStarterEx implements ApplicationStarter {
  @NotNull
  @Override
  public final Future<CliResult> processExternalCommandLineAsync(@NotNull List<String> args, @Nullable String currentDirectory) {
    processExternalCommandLine(ArrayUtilRt.toStringArray(args), currentDirectory);
    return CompletableFuture.completedFuture(CliResult.OK);
  }

  /**
   * @deprecated use async version {@link #processExternalCommandLineAsync}
   */
  @Deprecated(forRemoval = true)
  public void processExternalCommandLine(String @NotNull [] args, @Nullable String currentDirectory) {
    throw new UnsupportedOperationException("Class " + getClass().getName() + " must implement `processExternalCommandLine()`");
  }
}