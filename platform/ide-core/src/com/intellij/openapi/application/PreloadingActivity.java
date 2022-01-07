// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

/**
 * <p>An activity to be executed in background on IDE startup. It may load some classes or other configuration
 * so that when something (e.g. an action) is invoked the first time in the UI, there's no visible pause
 * while required stuff is being lazily loaded.</p>
 *
 * <p>Preloading activities should not have any side effects except for improving subsequent performance, so that
 * if they are not executed for any reason, the behavior of the IDE remains the same.</p>
 *
 * <p>Being eager and unspecific, preloading should be considered last resort in optimization.
 * Please prefer other ways of speeding up things, by e.g. reducing the amount of classloading and initialization
 * necessary when some functionality is invoked the first time.</p>
 */
public abstract class PreloadingActivity {
  /**
   * Perform the preloading.
   *
   * @param indicator a progress indicator for the background preloading process.
   *                  Canceled if the application has exited.
   *                  Long actions should periodically perform {@code indicator.checkCanceled()}.
   */
  public abstract void preload(@NotNull ProgressIndicator indicator);
}