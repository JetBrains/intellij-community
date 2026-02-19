// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util;

import com.intellij.openapi.application.CoroutinesKt;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A computation that needs to be run in background and inside a read action, and canceled whenever a write action is about to occur. 
 * 
 * @see ProgressIndicatorUtils#scheduleWithWriteActionPriority(ReadTask)
 *
 * @deprecated This class defines such contracts which ties the implementation to Swing.
 * Use {@link CoroutinesKt#readAction} for running a read action,
 * or {@link CoroutinesKt#readAndEdtWriteAction} for running a read followed by a write.
 */
@Deprecated
public abstract class ReadTask {
  /**
   * Performs the computation.
   * Is invoked inside a read action and under a progress indicator that's canceled when a write action is about to occur.
   * For tasks that have some Swing thread activity afterwards (e.g. applying changes, showing dialogs etc),
   * use {@link #performInReadAction(ProgressIndicator)} instead
   */
  public void computeInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
    throw new UnsupportedOperationException();
  }

  /**
   * Performs the computation.
   * Is invoked inside a read action and under a progress indicator that's canceled when a write action is about to occur.
   * @return an action that should be performed later on Swing thread if no write actions have happened before that
   */
  public @Nullable Continuation performInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
    computeInReadAction(indicator);
    return null;
  }

  /**
   * Is invoked on Swing thread whenever the computation is canceled by a write action.
   * A likely implementation is to restart the computation, maybe based on the new state of the system.
   */
  public abstract void onCanceled(@NotNull ProgressIndicator indicator);

  /**
   * Is invoked on a background thread. The responsibility of this method is to start a read action and 
   * call {@link #computeInReadAction(ProgressIndicator)}. Overriders might also do something else.
   * For example, use {@link com.intellij.openapi.project.DumbService#runReadActionInSmartMode(Runnable)}.
   * @param indicator the progress indicator of the background thread
   */
  public Continuation runBackgroundProcess(final @NotNull ProgressIndicator indicator) throws ProcessCanceledException {
    return ReadAction.compute(() -> performInReadAction(indicator));
  }

  /**
   * An object representing the action that should be done on Swing thread after the background computation is finished.
   * It's invoked only if tasks' progress indicator hasn't been canceled since the task has ended.
   * @deprecated Deprecated as part of {@link ReadTask}.
   */
  @Deprecated
  public static final class Continuation {
    private final Runnable myAction;
    private final ModalityState myModalityState;

    /**
     * @param action code to be executed in Swing thread in default modality state
     * @see ModalityState#defaultModalityState()
     */
    public Continuation(@NotNull Runnable action) {
      this(action, ModalityState.defaultModalityState());
    }

    /**
     * @param action code to be executed in Swing thread in default modality state
     * @param modalityState modality state when the action is to be executed
     */
    public Continuation(@NotNull Runnable action, @NotNull ModalityState modalityState) {
      myAction = action;
      myModalityState = modalityState;
    }

    /**
     * @return modality state when {@link #getAction()} is to be executed
     */
    public @NotNull ModalityState getModalityState() {
      return myModalityState;
    }

    /**
     * @return runnable to be executed in Swing thread in default modality state
     */
    public @NotNull Runnable getAction() {
      return myAction;
    }
  }
}
