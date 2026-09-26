// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

/**
 * @deprecated This is used only inside {@link BackgroundTaskQueue}
 */
@Deprecated
public interface PerformInBackgroundOption {
  /**
   * In this mode the corresponding {@link ProgressIndicator} will be shown in progress dialog with "Background" button.
   * Users may send the task to background.
   */
  PerformInBackgroundOption DEAF = () -> false;

  PerformInBackgroundOption ALWAYS_BACKGROUND = () -> true;

  boolean shouldStartInBackground();

  /**
   * @deprecated If a task should start in background, it starts in background without showing progress in the middle of the IDE frame.
   * This method is never invoked.
   */
  @Deprecated
  default void processSentToBackground() {
  }
}
