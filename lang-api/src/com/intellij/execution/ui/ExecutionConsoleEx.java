package com.intellij.execution.ui;

import org.jetbrains.annotations.NotNull;

/**
 * @author Gregory.Shrago
 */
public interface ExecutionConsoleEx extends ExecutionConsole {
  void buildUi(final RunnerLayoutUi layoutUi);
  @NotNull
  String getExecutionConsoleId();
}
