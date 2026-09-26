// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.impl;

import com.intellij.execution.process.ProcessHandler;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class ConsoleState {
  public abstract @NotNull ConsoleState attachTo(@NotNull ConsoleViewImpl console, @NotNull ProcessHandler processHandler);
  public abstract @NotNull ConsoleState dispose();

  public boolean isFinished() {
    return false;
  }

  public boolean isRunning() {
    return false;
  }

  /**
   * @return whether the given line should be folded as a command line if it's first in the console
   */
  public boolean isCommandLine(@NotNull String line) {
    return false;
  }

  public void sendUserInput(@NotNull String input) throws IOException {}

  public abstract static class NotStartedStated extends ConsoleState {
    @Override
    public @NotNull ConsoleState dispose() {
      // not disposable
      return this;
    }

    @Override
    public String toString() {
      return "Not started state";
    }
  }
}
