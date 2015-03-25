/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.application;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public interface ModalityInvokator {
  /**
   * Causes <i>runnable.run()</i> to be executed asynchronously on the
   * AWT event dispatching thread.  This will happen after all
   * pending AWT events have been processed.
   *
   * @param runnable the runnable to execute.
   */
  @NotNull
  ActionCallback invokeLater(@NotNull Runnable runnable);

  @NotNull
  ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull Condition expired);

  /**
   * Causes <i>runnable.run()</i> to be executed asynchronously on the
   * AWT event dispatching thread, when IDEA is in the specified modality
   * state.
   *
   * @param runnable the runnable to execute.
   * @param state the state in which the runnable will be executed.
   */
  @NotNull
  ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state);

  @NotNull
  ActionCallback invokeLater(@NotNull Runnable runnable, @NotNull ModalityState state, @NotNull Condition expired);
}