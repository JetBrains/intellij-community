// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.NonExtendable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.NlsContexts.Button;
import static com.intellij.openapi.util.NlsContexts.Tooltip;

@Experimental
@NonExtendable
public interface TaskCancellation {

  @NonExtendable
  interface Cancellable extends TaskCancellation {

    @Contract(value = "_ -> new", pure = true)
    @NotNull Cancellable withButtonText(@Button @NotNull String buttonText);

    @Contract(value = "_ -> new", pure = true)
    @NotNull Cancellable withTooltipText(@Tooltip @NotNull String tooltipText);
  }

  /**
   * @return an object, which means that the "Cancel" button should not be displayed in the UI
   */
  @Contract(pure = true)
  static @NotNull TaskCancellation nonCancellable() {
    return ApplicationManager.getApplication().getService(TaskSupport.class).taskCancellationNonCancellableInternal();
  }

  /**
   * @return an object, which means that the "Cancel" button should be displayed in the UI,
   * with optionally customized button text and/or tooltip text
   */
  @Contract(pure = true)
  static @NotNull Cancellable cancellable() {
    return ApplicationManager.getApplication().getService(TaskSupport.class).taskCancellationCancellableInternal();
  }
}
