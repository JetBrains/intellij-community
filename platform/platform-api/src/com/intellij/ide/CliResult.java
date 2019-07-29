// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.util.concurrency.FixedFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public final class CliResult {
  private static final FixedFuture<CliResult> OK_FUTURE = new FixedFuture<>(new CliResult(0, null));

  private final int myReturnCode;

  @Nullable
  private final String myMessage;

  public CliResult(int code, @Nullable String message) {
    myReturnCode = code;
    myMessage = message;
  }

  public int getReturnCode() {
    return myReturnCode;
  }

  @Nullable
  public String getMessage() {
    return myMessage;
  }

  @NotNull
  public static Future<CliResult> error(int exitCode, @Nullable String message) {
    return new FixedFuture<>(new CliResult(exitCode, message));
  }

  @NotNull
  public static Future<CliResult> ok() {
    return OK_FUTURE;
  }

  @NotNull
  public static CliResult getOrWrapFailure(@NotNull Future<CliResult> future, int timeoutCode) {
    try {
      return future.get();
    }
    catch (InterruptedException | ExecutionException e) {
      return new CliResult(timeoutCode, e.getMessage());
    }
  }
}
