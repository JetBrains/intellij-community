// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Consumer;

public interface ApplicationEx extends Application {
  String LOCATOR_FILE_NAME = ".home";
  String PRODUCT_INFO_FILE_NAME = "product-info.json";
  String PRODUCT_INFO_FILE_NAME_MAC = "Resources/" + PRODUCT_INFO_FILE_NAME;

  int FORCE_EXIT = 0x01;
  int EXIT_CONFIRMED = 0x02;
  int SAVE = 0x04;
  int ELEVATE = 0x08;

  /**
   * @return true if the EDT is performing write action right now.
   * @see #runWriteAction(Runnable)
   */
  boolean isWriteActionInProgress();

  /**
   * @return true if the EDT started to acquire write action but has not acquired it yet.
   * @see #runWriteAction(Runnable)
   */
  boolean isWriteActionPending();

  void setSaveAllowed(boolean value);

  default void exit(int flags) {
    exit();
  }

  default void exit(int flags, int exitCode) {
    exit();
  }

  @Override
  default void exit() {
    exit(isSaveAllowed() ? SAVE : 0);
  }

  /**
   * @param force         when {@code true}, no additional confirmations will be shown. The application is guaranteed to exit
   * @param exitConfirmed when {@code true}, suppresses any shutdown confirmation. However, if there are any background processes or tasks running,
   *                      a corresponding confirmation will be shown with the possibility to cancel the operation
   * @param exitCode      set when you want exitCode to be different from default 0
   */
  default void exit(boolean force, boolean exitConfirmed, int exitCode) {
    int flags = SAVE;
    if (force) {
      flags |= FORCE_EXIT;
    }
    if (exitConfirmed) {
      flags |= EXIT_CONFIRMED;
    }
    exit(flags, exitCode);
  }

  default void exit(boolean force, boolean exitConfirmed) {
    exit(force, exitConfirmed, 0);
  }

  /**
   * @param exitConfirmed when {@code true}, suppresses any shutdown confirmation. However, if there are any background processes or tasks running,
   *                      a corresponding confirmation will be shown with the possibility to cancel the operation
   */
  void restart(boolean exitConfirmed);

  @Override
  default void restart() {
    restart(false);
  }

  /**
   * Restarts the IDE with optional process elevation (on Windows).
   *
   * @param exitConfirmed if true, the IDE does not ask for exit confirmation.
   * @param elevate       if true and the IDE is running on Windows, the IDE is restarted in elevated mode (with admin privileges)
   */
  void restart(boolean exitConfirmed, boolean elevate);

  /**
   * Runs a modal process.
   * For internal use only, see {@link Task}.
   * Consider also {@link ProgressManager#runProcessWithProgressSynchronously}
   */
  @ApiStatus.Internal
  default boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                                      @NotNull @NlsContexts.ProgressTitle String progressTitle,
                                                      boolean canBeCanceled,
                                                      Project project) {
    return runProcessWithProgressSynchronously(process, progressTitle, canBeCanceled, true, project, null, null);
  }

  /**
   * Runs a modal or non-modal process.
   * For internal use only, see {@link Task}.
   * Consider also {@link ProgressManager#runProcessWithProgressSynchronously}
   */
  @ApiStatus.Internal
  boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                              @NotNull @NlsContexts.ProgressTitle String progressTitle,
                                              boolean canBeCanceled,
                                              boolean shouldShowModalWindow,
                                              @Nullable Project project,
                                              @Nullable JComponent parentComponent,
                                              @Nullable @Nls(capitalization = Nls.Capitalization.Title) String cancelText);

  void assertIsDispatchThread(@Nullable JComponent component);

  /**
   * Tries to acquire the read lock and run the {@code action}.
   *
   * @return true if the action was run while holding the lock, false if was unable to get the lock and the action was not run
   */
  boolean tryRunReadAction(@NotNull Runnable action);

  /** DO NOT USE */
  @ApiStatus.Internal
  default void executeByImpatientReader(@NotNull Runnable runnable) throws ApplicationUtil.CannotRunReadActionException {
    runnable.run();
  }

  @ApiStatus.Experimental
  default boolean runWriteActionWithCancellableProgressInDispatchThread(@NotNull @NlsContexts.ProgressTitle String title,
                                                                        @Nullable Project project,
                                                                        @Nullable JComponent parentComponent,
                                                                        @NotNull Consumer<? super ProgressIndicator> action) {
    throw new UnsupportedOperationException();
  }

  @ApiStatus.Experimental
  default boolean runWriteActionWithNonCancellableProgressInDispatchThread(@NotNull @NlsContexts.ProgressTitle String title,
                                                                           @Nullable Project project,
                                                                           @Nullable JComponent parentComponent,
                                                                           @NotNull Consumer<? super ProgressIndicator> action) {
    throw new UnsupportedOperationException();
  }

  /**
   * DO NOT USE
   */
  @ApiStatus.Internal
  default boolean isInImpatientReader() {
    return false;
  }

  /**
   * Runs the specified action, releasing the write-intent lock if it is acquired at the moment of the call.
   * <p>
   * This method is used to implement higher-level API. Please do not use it directly.
   */
  @ApiStatus.Internal
  default <T, E extends Throwable> T runUnlockingIntendedWrite(@NotNull ThrowableComputable<T, E> action) throws E {
    return action.compute();
  }

  /**
   * Runs the specified action under the write-intent lock. Can be called from any thread. The action is executed immediately
   * if no write-intent action is currently running or blocked until the currently running write-intent action completes.
   * <p>
   * This method is used to implement higher-level API. Please do not use it directly.
   * Use {@link #invokeLaterOnWriteThread}, {@link com.intellij.openapi.application.WriteThread} or
   * {@link com.intellij.openapi.application.AppUIExecutor#onWriteThread()} to run code under the write-intent lock asynchronously.
   *
   * @param action the action to run
   */
  @ApiStatus.Internal
  default void runIntendedWriteActionOnCurrentThread(@NotNull Runnable action) {
    action.run();
  }

  default boolean isLightEditMode() {
    return false;
  }

  default boolean isComponentCreated() {
    return true;
  }

  // in some cases, we cannot get service by class

  /**
   * @deprecated Use {@link com.intellij.ide.IdeEventQueue#flushNativeEventQueue IdeEventQueue.flushNativeEventQueue()}
   */
  @ApiStatus.Internal
  @Deprecated
  default void flushNativeEventQueue() {}

  @ApiStatus.Internal
  default void dispatchCoroutineOnEDT(Runnable runnable, ModalityState state) {
    invokeLater(runnable, state, Conditions.alwaysFalse());
  }

  @ApiStatus.Internal
  default void addReadActionListener(@NotNull ReadActionListener listener, @NotNull Disposable parentDisposable) { }

  @ApiStatus.Experimental
  default void addWriteActionListener(@NotNull WriteActionListener listener, @NotNull Disposable parentDisposable) { }

  @ApiStatus.Internal
  default void addWriteIntentReadActionListener(@NotNull WriteIntentReadActionListener listener, @NotNull Disposable parentDisposable) { }

  @ApiStatus.Internal
  default void addLockAcquisitionListener(@NotNull LockAcquisitionListener listener, @NotNull Disposable parentDisposable) { }

  @ApiStatus.Internal
  @ApiStatus.Obsolete
  default void addSuspendingWriteActionListener(@NotNull WriteLockReacquisitionListener listener, @NotNull Disposable parentDisposable) { }

  @ApiStatus.Internal
  default void prohibitTakingLocksInsideAndRun(@NotNull Runnable runnable, boolean failSoftly, @NlsSafe String advice) {
    runnable.run();
  }

  /**
   * Similar to {@link #invokeAndWait(Runnable, ModalityState)}, but does not take the Write-Intent lock inside.
   * This is useful when you still need to schedule a computation with the required modality state, but don't want to acqure the WI lock inside.
   * In the future, this method may go public
   */
  @ApiStatus.Internal
  default void invokeAndWaitRelaxed(@NotNull Runnable runnable, @NotNull ModalityState modalityState) {
    invokeAndWait(runnable, modalityState);
  }

  @ApiStatus.Internal
  default void allowTakingLocksInsideAndRun(@NotNull Runnable runnable) {
    runnable.run();
  }
}
