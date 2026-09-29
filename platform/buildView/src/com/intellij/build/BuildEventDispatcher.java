// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build;

import com.intellij.execution.process.ProcessOutputType;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.NonExtendable;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.util.function.Consumer;

/**
 * Makes the build view events from the output of an external build process.
 * <p>
 * Orchestrates everything that processes and renders the build output:
 * <ul>
 *   <li>the markup of the build output, that attributes its text to the tasks reported by the build events;</li>
 *   <li>the output parsers, that mark up the errors in the output, provide explanation and suggest quick fixes.</li>
 * </ul>
 *
 * @author Vladislav.Soroka
 */
@Internal
@NonExtendable
public interface BuildEventDispatcher extends Closeable, BuildProgressListener {

  /**
   * Dispatches a chunk of the build process output.
   *
   * @param outputType the output stream that produced the {@code text}.
   */
  void onTextAvailable(@NotNull String text, @NotNull ProcessOutputType outputType);

  /**
   * Registers handler which is invoked once the build process is finished and the build messages are dispatched.
   */
  default void invokeOnCompletion(@NotNull Consumer<? super Throwable> consumer) {
    throw new UnsupportedOperationException("invokeOnCompletion is not supported by this BuildEventDispatcher");
  }

  /**
   * Executes completion handlers and finalizes the build process orchestration.
   */
  @Override
  default void close() {}
}
