// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything.execution;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class RunAnythingRunProfile implements RunProfile {
  private final @NotNull String myOriginalCommand;
  private final @NotNull GeneralCommandLine myCommandLine;

  public RunAnythingRunProfile(@NotNull GeneralCommandLine commandLine,
                               @NotNull String originalCommand) {
    myCommandLine = commandLine;
    myOriginalCommand = originalCommand;
  }

  @Override
  public @Nullable RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
    return new RunAnythingRunProfileState(environment, myOriginalCommand);
  }

  @Override
  public @NotNull String getName() {
    return myOriginalCommand;
  }

  public @NotNull String getOriginalCommand() {
    return myOriginalCommand;
  }

  public @NotNull GeneralCommandLine getCommandLine() {
    return myCommandLine;
  }

  @Override
  public @Nullable Icon getIcon() {
    return AllIcons.Actions.Run_anything;
  }
}