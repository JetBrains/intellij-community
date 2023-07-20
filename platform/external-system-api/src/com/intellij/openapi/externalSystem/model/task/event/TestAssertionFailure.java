// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.task.event;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TestAssertionFailure extends TestFailure {

  private final @NotNull String expectedText;
  private final @NotNull String actualText;
  private final @Nullable String expectedFile;
  private final @Nullable String actualFile;

  public TestAssertionFailure(
    @Nullable String exceptionName,
    @Nullable @Nls String message,
    @Nullable @Nls String stackTrace,
    @Nullable @Nls String description,
    @NotNull List<Failure> causes,
    @NotNull String expectedText,
    @NotNull String actualText,
    @Nullable String expectedFile,
    @Nullable String actualFile
  ) {
    super(exceptionName, message, stackTrace, description, causes, false);
    this.expectedText = expectedText;
    this.actualText = actualText;
    this.expectedFile = expectedFile;
    this.actualFile = actualFile;
  }

  public TestAssertionFailure(
    @Nullable String exceptionName,
    @Nullable @Nls String message,
    @Nullable @Nls String stackTrace,
    @Nullable @Nls String description,
    @NotNull List<Failure> causes,
    @NotNull String expectedText,
    @NotNull String actualText
  ) {
    this(exceptionName, message, stackTrace, description, causes, expectedText, actualText, null, null);
  }

  public @NotNull String getExpectedText() {
    return expectedText;
  }

  public @NotNull String getActualText() {
    return actualText;
  }

  public @Nullable String getExpectedFile() {
    return expectedFile;
  }

  public @Nullable String getActualFile() {
    return actualFile;
  }
}
