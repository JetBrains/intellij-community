// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CliResult {
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
    return new LightDoneFuture(exitCode, message);
  }

  public static Future<CliResult> ok() {
    return new LightDoneFuture(0, null);
  }
  
  private static final class LightDoneFuture implements Future<CliResult> {
    private final CliResult myResult;

    private LightDoneFuture(int returnCode, @Nullable String message) {
      myResult = new CliResult(returnCode, message);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public boolean isDone() {
      return true;
    }

    @Override
    public CliResult get() {
      return myResult;
    }

    @Override
    public CliResult get(long timeout, @NotNull TimeUnit unit) {
      return myResult;
    }
  }
}
