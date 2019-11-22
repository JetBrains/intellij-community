// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public final class CliResult {
  public static final CliResult OK = new CliResult(0, null);
  public static final Future<CliResult> OK_FUTURE = CompletableFuture.completedFuture(OK);

  public final int exitCode;
  public final @Nullable String message;

  public CliResult(int exitCode, @Nullable String message) {
    this.exitCode = exitCode;
    this.message = message;
  }

  public static @NotNull Future<CliResult> error(int exitCode, @Nullable String message) {
    return CompletableFuture.completedFuture(new CliResult(exitCode, message));
  }

  public static @NotNull CliResult unmap(@NotNull Future<CliResult> future, int timeoutCode) {
    try {
      return future.get();
    }
    catch (InterruptedException | ExecutionException e) {
      return new CliResult(timeoutCode, e.getMessage());
    }
  }
}