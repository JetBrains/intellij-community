// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ConsoleView extends ExecutionConsole {
  void print(@NotNull String text, @NotNull ConsoleViewContentType contentType);

  void clear();

  void scrollTo(int offset);

  void attachToProcess(@NotNull ProcessHandler processHandler);

  default void requestScrollingToEnd() {}

  void setOutputPaused(boolean value);

  boolean isOutputPaused();

  boolean hasDeferredOutput();

  void performWhenNoDeferredOutput(@NotNull Runnable runnable);

  void setHelpId(@NotNull String helpId);

  void addMessageFilter(@NotNull Filter filter);

  void printHyperlink(@NotNull String hyperlinkText, @Nullable HyperlinkInfo info);

  int getContentSize();

  boolean canPause();

  AnAction @NotNull [] createConsoleActions();

  void allowHeavyFilters();

  default @NotNull ConsoleViewPlace getPlace() {
    return ConsoleViewPlace.UNKNOWN;
  }
}