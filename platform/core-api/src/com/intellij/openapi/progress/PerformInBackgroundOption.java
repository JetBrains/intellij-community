// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress;

public interface PerformInBackgroundOption {
  /**
   * In this mode the corresponding {@link ProgressIndicator} will be shown in progress dialog with "Background" button.
   * Users may send the task to background.
   */
  PerformInBackgroundOption DEAF = () -> false;

  PerformInBackgroundOption ALWAYS_BACKGROUND = () -> true;

  boolean shouldStartInBackground();

  default void processSentToBackground() {
  }
}
