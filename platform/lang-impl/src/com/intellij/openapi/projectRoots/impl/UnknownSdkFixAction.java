// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface UnknownSdkFixAction {
  @NotNull @Nls String getActionShortText();

  @NotNull @Nls String getActionDetailedText();

  default @Nullable @Nls String getActionTooltipText() {
    return null;
  }

  /**
   * Starts the fix action and forgets about it running.
   * The implementation is responsible to implement necessary
   * progress dialogs, invoke later calls and so on
   */
  void applySuggestionAsync();

  /**
   * Applies suggestions in a modal progress, e.g. as a part of
   * the {@link UnknownSdkModalNotification}
   */
  void applySuggestionModal(@NotNull ProgressIndicator indicator);
}
