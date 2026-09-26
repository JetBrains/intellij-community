// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/// An interface that can be implemented by the ProgressIndicator to be called from CheckCanceledHook interface.
public interface PingProgress extends CoreProgressManager.CheckCanceledHook {
  void interact();

  /// When on the UI thread under a PingProgress, invoke its [#interact()].
  /// This might, for example, repaint the progress to give the user feedback
  /// that the IDE is working on a long-running operation and not frozen.
  static void interactWithEdtProgress() {
    @SuppressWarnings("UsagesOfObsoleteApi") var indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (indicator instanceof PingProgress pp && EDT.isCurrentThreadEdt()) {
      pp.interact();
    }
  }

  @ApiStatus.Internal
  @Override
  default boolean runHook(@Nullable ProgressIndicator indicator) {
    interact();
    return false;
  }
}
