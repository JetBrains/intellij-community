// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.execution;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Essentially this class is {@link ExternalSystemTaskExecutionSettings} plus auxiliary information like execution type (run/debug etc).
 */
public class ExternalTaskExecutionInfo {
  
  private @NotNull ExternalSystemTaskExecutionSettings mySettings;
  private @NotNull String myExecutorId;

  @SuppressWarnings("UnusedDeclaration")
  public ExternalTaskExecutionInfo() {
    this(new ExternalSystemTaskExecutionSettings(), "___DUMMY___");
  }

  public ExternalTaskExecutionInfo(@NotNull ExternalSystemTaskExecutionSettings settings, @NotNull String executorId) {
    mySettings = settings;
    myExecutorId = executorId;
  }

  public @NotNull ExternalSystemTaskExecutionSettings getSettings() {
    return mySettings;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setSettings(@NotNull ExternalSystemTaskExecutionSettings settings) {
    // Required by IJ serialization.
    mySettings = settings;
  }

  public @NotNull String getExecutorId() {
    return myExecutorId;
  }

  @SuppressWarnings("UnusedDeclaration")
  public void setExecutorId(@NotNull String executorId) {
    // Required by IJ serialization.
    myExecutorId = executorId;
  }

  public @Nls String getDescription() {
    return StringUtil.join(mySettings.getTaskDescriptions(), "\n");
  }

  @Override
  public int hashCode() {
    int result = mySettings.hashCode();
    result = 31 * result + myExecutorId.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExternalTaskExecutionInfo task = (ExternalTaskExecutionInfo)o;

    if (!myExecutorId.equals(task.myExecutorId)) return false;
    if (!mySettings.equals(task.mySettings)) return false;

    return true;
  }

  @Override
  public String toString() {
    return StringUtil.join(mySettings.getTaskNames(), " ");
  }
}
