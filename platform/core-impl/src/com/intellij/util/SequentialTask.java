// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

/**
 * Defines general contract for processing that may be executed by parts, i.e. it remembers the state after every iteration
 * and allows to resume the processing any time.
 *
 * @author Denis Zhdanov
 */
public interface SequentialTask {
  /**
   * Callback method that is assumed to be called before the processing.
   */
  default void prepare() { }

  /**
   * Returns {@code true} if the processing is complete, {@code false} otherwise.
   */
  boolean isDone();

  /**
   * Asks the current task to perform one more processing iteration.
   *
   * @return {@code true} if the processing is done, {@code false} otherwise.
   */
  boolean iteration();

  default boolean iteration(@NotNull ProgressIndicator indicator) {
    return iteration();
  }

  /**
   * Asks the current task to stop the processing (if any).
   */
  default void stop() { }
}