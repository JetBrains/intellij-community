// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 * @see Application#getInvokator()
 */
public interface ModalityInvokator {
  /**
   * Causes {@code runnable.run()} to be executed asynchronously on the
   * AWT event dispatching thread. This will happen after all
   * pending AWT events have been processed.
   *
   * @param runnable the runnable to execute.
   */
  @NotNull
  ActionCallback invokeLater(@NotNull Runnable runnable);

  @NotNull
  ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull Condition expired);

  /**
   * Causes {@code runnable.run()} to be executed asynchronously on the
   * AWT event dispatching thread, when the IDE is in the specified modality
   * state.
   *
   * @param runnable the runnable to execute.
   * @param state    the state in which the runnable will be executed.
   */
  @NotNull
  ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state);

  @NotNull
  ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state, @NotNull Condition expired);
}