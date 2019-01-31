// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public interface IdePerformanceListener {
  Topic<IdePerformanceListener> TOPIC = Topic.create("IdePerformanceListener", IdePerformanceListener.class);

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
   * @param lengthInSeconds approximate length in seconds of the interval that the IDE was unresponsive
   */
  default void uiFreezeFinished(int lengthInSeconds) {
  }

  /**
   * Invoked on each UI response sampled every <code>performance.watcher.sampling.interval.ms</code> set in the Registry.
   *
   * @param latencyMs time between scheduling a UI event and executing it, in milliseconds
   */
  default void uiResponded(long latencyMs) {
  }
}
