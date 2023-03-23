// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.task.event;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TestAssertionFailure implements Failure {

  private final @NotNull @Nls String message;
  private final @Nullable @Nls String description;
  private final @NotNull List<Failure> causes;
  private final @NotNull String expectedText;
  private final @NotNull String actualText;
  private final @Nullable String expectedFile;
  private final @Nullable String actualFile;

  public TestAssertionFailure(
    @NotNull @Nls String message,
    @Nullable @Nls String description,
    @NotNull List<Failure> causes,
    @NotNull String expectedText,
    @NotNull String actualText,
    @Nullable String expectedFile,
    @Nullable String actualFile
  ) {
    this.message = message;
    this.description = description;
    this.causes = causes;
    this.expectedText = expectedText;
    this.actualText = actualText;
    this.expectedFile = expectedFile;
    this.actualFile = actualFile;
  }

  @Override
  public @NotNull @Nls String getMessage() {
    return message;
  }

  @Override
  public @Nullable @Nls String getDescription() {
    return description;
  }

  @Override
  public @NotNull List<? extends Failure> getCauses() {
    return causes;
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
