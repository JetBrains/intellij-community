package com.intellij.execution.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * @author Gregory.Shrago
 */
public interface ExecutionConsoleEx extends ExecutionConsole {
  void buildUi(final RunnerLayoutUi layoutUi);

  @NonNls @NotNull
  String getExecutionConsoleId();
}
