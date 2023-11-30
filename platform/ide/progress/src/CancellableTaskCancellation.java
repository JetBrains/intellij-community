// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress;

import com.intellij.openapi.util.NlsContexts.Button;
import com.intellij.openapi.util.NlsContexts.Tooltip;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Internal
public final class CancellableTaskCancellation implements TaskCancellation.Cancellable {

  static final Cancellable DEFAULT = new CancellableTaskCancellation(null, null);

  private final @Button @Nullable String buttonText;
  private final @Tooltip @Nullable String tooltipText;

  private CancellableTaskCancellation(
    @Button @Nullable String buttonText,
    @Tooltip @Nullable String tooltipText
  ) {
    this.buttonText = buttonText;
    this.tooltipText = tooltipText;
  }

  public @Button @Nullable String getButtonText() {
    return buttonText;
  }

  public @Tooltip @Nullable String getTooltipText() {
    return tooltipText;
  }

  @Override
  public @NotNull Cancellable withButtonText(@NotNull String buttonText) {
    return new CancellableTaskCancellation(buttonText, this.tooltipText);
  }

  @Override
  public @NotNull Cancellable withTooltipText(@NotNull String tooltipText) {
    return new CancellableTaskCancellation(this.buttonText, tooltipText);
  }
}
