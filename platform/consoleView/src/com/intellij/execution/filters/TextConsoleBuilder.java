// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.execution.ui.ConsoleView;
import org.jetbrains.annotations.ApiStatus;
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

  /**
   * Determines the default modality state for console updates
   * <p>
   * If set to {@code false} (the default), the console defaults to "no modality",
   * which means that it won't update its contents when a modal dialog is shown.
   * If set to {@code true}, the console will use its own component to determine the modality state.
   * Which means the console located in a modal dialog will be updated as long as there are no other
   * modal dialogs on top of it.
   * </p>
   * <p>
   * The default implementation does nothing.
   * It must be overridden by a specific builder to have any effect.
   * Moreover, the behavior set by this flag may be overridden or ignored by specific console implementations.
   * </p>
   */
  @ApiStatus.Internal
  public void setUseOwnModalityStateForUpdates(boolean useOwnModalityStateForUpdates) {
  }

  /**
   * Determines whether {@link ConsoleView#attachToProcess} prints the process output into the console.
   * <p>
   * If set to {@code true} (the default), the console prints the process output.
   * If set to {@code false}, the console owner prints the process output into the console.
   * </p>
   * <p>
   * The default implementation supports only {@code true}.
   * It must be overridden by a specific builder to support {@code false}.
   * </p>
   */
  @ApiStatus.Internal
  public @NotNull TextConsoleBuilder setAttachToProcessOutput(boolean attachToProcessOutput) {
    if (!attachToProcessOutput) {
      throw new UnsupportedOperationException(getClass().getName() + " doesn't support consoles detached from the process output");
    }
    return this;
  }

  /**
   * Determines whether {@link ConsoleView#attachToProcess} sends the user input of the console into the process input.
   * <p>
   * If set to {@code true} (the default), the console sends the user input into the process input.
   * If set to {@code false}, the console doesn't accept the user input.
   * </p>
   * <p>
   * The default implementation supports only {@code true}.
   * It must be overridden by a specific builder to support {@code false}.
   * </p>
   */
  @ApiStatus.Internal
  public @NotNull TextConsoleBuilder setAttachToProcessInput(boolean attachToProcessInput) {
    if (!attachToProcessInput) {
      throw new UnsupportedOperationException(getClass().getName() + " doesn't support consoles detached from the process input");
    }
    return this;
  }
}