/*
 * @author max
 */
package com.intellij.openapi.application;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

public interface ModalityInvokator {
  /**
   * Causes <i>runnable.run()</i> to be executed asynchronously on the
   * AWT event dispatching thread.  This will happen after all
   * pending AWT events have been processed.
   *
   * @param runnable the runnable to execute.
   */
  ActionCallback invokeLater(Runnable runnable);

  ActionCallback invokeLater(Runnable runnable, @NotNull Condition expired);

  /**
   * Causes <i>runnable.run()</i> to be executed asynchronously on the
   * AWT event dispatching thread, when IDEA is in the specified modality
   * state.
   *
   * @param runnable the runnable to execute.
   * @param state the state in which the runnable will be executed.
   */
  ActionCallback invokeLater(Runnable runnable, @NotNull ModalityState state);

  ActionCallback invokeLater(Runnable runnable, @NotNull ModalityState state, @NotNull Condition expired);
}