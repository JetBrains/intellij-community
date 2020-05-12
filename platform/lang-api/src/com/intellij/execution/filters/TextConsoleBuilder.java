// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.execution.ui.ConsoleView;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class TextConsoleBuilder {
  public abstract @NotNull ConsoleView getConsole();

  public abstract void addFilter(@NotNull Filter filter);

  public abstract void setViewer(boolean isViewer);

  public @NotNull TextConsoleBuilder filters(Filter @NotNull ... filters) {
    for (Filter filter : filters) {
      addFilter(filter);
    }
    return this;
  }

  public @NotNull TextConsoleBuilder filters(@NotNull List<? extends Filter> filters) {
    for (Filter filter : filters) {
      addFilter(filter);
    }
    return this;
  }
}