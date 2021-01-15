// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public interface IdePerformanceListener {
  @Topic.AppLevel
  Topic<IdePerformanceListener> TOPIC = new Topic<>(IdePerformanceListener.class, Topic.BroadcastDirection.NONE, true);

  /**
   * Invoked after thread state has been dumped to a file.
   */
  default void dumpedThreads(@NotNull File toFile, @NotNull ThreadDump dump) {
  }

  /**
   * Invoked when IDE has detected that the UI hasn't responded for some time (5 seconds by default)
   */
  default void uiFreezeStarted() {
  }

  /**
   * Invoked after the UI has become responsive again following a {@link #uiFreezeStarted()} event.
   * @param durationMs freeze duration in milliseconds
   * @param reportDir folder where all freeze report data is collected
   */
  default void uiFreezeFinished(long durationMs, @Nullable File reportDir) {
  }

  /**
   * Invoked on each UI response sampled every <code>performance.watcher.sampling.interval.ms</code> set in the Registry.
   *
   * @param latencyMs time between scheduling a UI event and executing it, in milliseconds
   */
  default void uiResponded(long latencyMs) {
  }
}
