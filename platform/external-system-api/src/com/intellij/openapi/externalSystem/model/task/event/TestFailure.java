// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.task.event;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TestFailure extends FailureImpl {

  private final @Nullable String exceptionName;
  private final @Nullable String stackTrace;
  private final boolean isTestError;

  public TestFailure(
    @Nullable String exceptionName,
    @Nullable @Nls String message,
    @Nullable @Nls String stackTrace,
    @Nullable @Nls String description,
    @NotNull List<? extends Failure> causes,
    boolean isTestError
  ) {
    super(message, description, causes);
    this.exceptionName = exceptionName;
    this.stackTrace = stackTrace;
    this.isTestError = isTestError;
  }

  public @Nullable String getExceptionName() {
    return exceptionName;
  }

  public @Nullable String getStackTrace() {
    return stackTrace;
  }

  public boolean isTestError() {
    return isTestError;
  }
}
