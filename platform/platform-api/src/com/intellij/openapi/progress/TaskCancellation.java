// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.CommonBundle;
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

  /**
   * @return a cancellation instance, which means that the cancel button should not be displayed in the UI
   */
  @Contract(pure = true)
  static @NotNull NonCancellable nonCancellable() {
    return ApplicationManager.getApplication().getService(TaskSupport.class).taskCancellationNonCancellableInternal();
  }

  @NonExtendable
  interface NonCancellable extends TaskCancellation {
  }

  /**
   * The returned instance can optionally be customized with button text and/or tooltip text.
   * If {@link Cancellable#withButtonText the button text} is not specified,
   * then {@link CommonBundle#getCancelButtonText the default text} is used.
   *
   * @return a cancellation instance, which means that the cancel button should be displayed in the UI
   */
  @Contract(pure = true)
  static @NotNull Cancellable cancellable() {
    return ApplicationManager.getApplication().getService(TaskSupport.class).taskCancellationCancellableInternal();
  }

  @NonExtendable
  interface Cancellable extends TaskCancellation {

    @Contract(value = "_ -> new", pure = true)
    @NotNull Cancellable withButtonText(@Button @NotNull String buttonText);

    @Contract(value = "_ -> new", pure = true)
    @NotNull Cancellable withTooltipText(@Tooltip @NotNull String tooltipText);
  }
}
