// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress;

import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.NlsContexts.Button;
import static com.intellij.openapi.util.NlsContexts.Tooltip;

@Experimental
public sealed interface TaskCancellation
  permits TaskCancellation.NonCancellable,
          TaskCancellation.Cancellable {

  /**
   * @return a cancellation instance, which means that the cancel button should not be displayed in the UI
   */
  @Contract(pure = true)
  static @NotNull NonCancellable nonCancellable() {
    return NonCancellableTaskCancellation.INSTANCE;
  }

  sealed interface NonCancellable extends TaskCancellation
    permits NonCancellableTaskCancellation {
  }

  /**
   * The returned instance can optionally be customized with button text and/or tooltip text.
   * If {@link Cancellable#withButtonText the button text} is not specified,
   * then {@link com.intellij.CommonBundle#getCancelButtonText the default text} is used.
   *
   * @return a cancellation instance, which means that the cancel button should be displayed in the UI
   */
  @Contract(pure = true)
  static @NotNull Cancellable cancellable() {
    return CancellableTaskCancellation.DEFAULT;
  }

  sealed interface Cancellable extends TaskCancellation
    permits CancellableTaskCancellation {

    @Contract(value = "_ -> new", pure = true)
    @NotNull Cancellable withButtonText(@Button @NotNull String buttonText);

    @Contract(value = "_ -> new", pure = true)
    @NotNull Cancellable withTooltipText(@Tooltip @NotNull String tooltipText);
  }
}
