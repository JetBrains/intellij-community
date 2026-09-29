// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.NonExtendable;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.util.function.Consumer;

/**
 * @author Vladislav.Soroka
 */
@Internal
@NonExtendable
public interface BuildEventDispatcher extends Closeable, BuildProgressListener {

  void onTextAvailable(@NotNull String text);

  /**
   * Registers handler which is invoked once the build process is finished and the build messages are dispatched.
   *
   * @throws UnsupportedOperationException if underlying implementation doesn't support {@code invokeOnCompletion()}.
   */
  default void invokeOnCompletion(@NotNull Consumer<? super Throwable> consumer) {
    throw new UnsupportedOperationException("invokeOnCompletion is not supported by this BuildEventDispatcher");
  }

  @Override
  default void close() {}

  default void setStdOut(boolean stdOut) {}
}
