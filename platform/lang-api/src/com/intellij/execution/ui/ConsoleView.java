/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @NotNull
  default ConsoleViewPlace getPlace() {
    return ConsoleViewPlace.UNKNOWN;
  }
}