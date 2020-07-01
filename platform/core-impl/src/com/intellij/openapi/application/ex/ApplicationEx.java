// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.Path;

public interface ApplicationEx extends Application {
  String LOCATOR_FILE_NAME = ".home";

  int FORCE_EXIT = 0x01;
  int EXIT_CONFIRMED = 0x02;
  int SAVE = 0x04;
  int ELEVATE = 0x08;

  /**
   * Loads the application configuration from the specified path
   *
   * @param configPath Path to /config folder
   */
  void load(@Nullable Path configPath);

  default void load() {
    load(null);
  }

  /**
   * @return true if this thread is inside read action.
   * @see #runReadAction(Runnable)
   */
  boolean holdsReadLock();

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

  /**
   * Acquires IW lock if it's not acquired by the current thread.
   *
   * @param invokedClassFqn fully qualified name of the class requiring the write intent lock.
   */
  @ApiStatus.Internal
  default void acquireWriteIntentLock(@NotNull String invokedClassFqn) { }

  /**
   * Releases IW lock.
   */
  @ApiStatus.Internal
  default void releaseWriteIntentLock() {}

  boolean isSaveAllowed();

  void setSaveAllowed(boolean value);

  /**
   * @deprecated use {@link #setSaveAllowed(boolean)} with {@code false}
   */
  @Deprecated
  default void doNotSave() {
    setSaveAllowed(false);
  }

  /**
   * Executes {@code process} in a separate thread in the application thread pool (see {@link #executeOnPooledThread(Runnable)}).
   * The process is run inside read action (see {@link #runReadAction(Runnable)})
   * If run from EDT, it is guaranteed that no other read or write action is run before the process start running.
   * If the process is running for too long, a progress window shown with {@code progressTitle} and a button with {@code cancelText}.
   * @return true if process run successfully and was not canceled.
   */
  boolean runProcessWithProgressSynchronouslyInReadAction(@Nullable Project project,
                                                          @NotNull String progressTitle,
                                                          boolean canBeCanceled,
                                                          String cancelText,
                                                          JComponent parentComponent,
                                                          @NotNull Runnable process);

  default void exit(@SuppressWarnings("unused") int flags) {
    exit();
  }

  @Override
  default void exit() {
    exit(SAVE);
  }

  /**
   * @param force if true, no additional confirmations will be shown. The application is guaranteed to exit
   * @param exitConfirmed if true, suppresses any shutdown confirmation. However, if there are any background processes or tasks running,
   *                      a corresponding confirmation will be shown with the possibility to cancel the operation
   */
  default void exit(boolean force, boolean exitConfirmed) {
    int flags = SAVE;
    if (force) {
      flags |= FORCE_EXIT;
    }
    if (exitConfirmed) {
      flags |= EXIT_CONFIRMED;
    }
    exit(flags);
  }

  /**
   * @param exitConfirmed if true, suppresses any shutdown confirmation. However, if there are any background processes or tasks running,
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
   * Runs modal process. For internal use only, see {@link Task}
   */
  @ApiStatus.Internal
  default boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                                      @NotNull @NlsContexts.ProgressTitle String progressTitle,
                                                      boolean canBeCanceled,
                                                      Project project) {
    return runProcessWithProgressSynchronously(process, progressTitle, canBeCanceled, true, project, null, null);
  }

  /**
   * Runs modal or non-modal process.
   * For internal use only, see {@link Task}
   */
  @ApiStatus.Internal
  boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                              @NotNull @NlsContexts.ProgressTitle String progressTitle,
                                              boolean canBeCanceled,
                                              boolean shouldShowModalWindow,
                                              @Nullable Project project,
                                              JComponent parentComponent,
                                              @Nullable @Nls(capitalization = Nls.Capitalization.Title) String cancelText);

  void assertIsDispatchThread(@Nullable JComponent component);

  void assertTimeConsuming();

  /**
   * Tries to acquire the read lock and run the {@code action}
   *
   * @return true if action was run while holding the lock, false if was unable to get the lock and action was not run
   */
  boolean tryRunReadAction(@NotNull Runnable action);

  /** DO NOT USE */
  @ApiStatus.Internal
  default void executeByImpatientReader(@NotNull Runnable runnable) throws ApplicationUtil.CannotRunReadActionException {
    runnable.run();
  }

  @ApiStatus.Experimental
  default boolean runWriteActionWithCancellableProgressInDispatchThread(@NotNull String title,
                                                                        @Nullable Project project,
                                                                        @Nullable JComponent parentComponent,
                                                                        @NotNull Consumer<? super ProgressIndicator> action) {
    throw new UnsupportedOperationException();
  }

  @ApiStatus.Experimental
  default boolean runWriteActionWithNonCancellableProgressInDispatchThread(@NotNull String title,
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
   * Runs the specified action, releasing Write Intent lock if it is acquired at the moment of the call.
   * <p>
   * This method is used to implement higher-level API, please do not use it directly.
   */
  @ApiStatus.Internal
  default <T, E extends Throwable> T runUnlockingIntendedWrite(@NotNull ThrowableComputable<T, E> action) throws E {
    return action.compute();
  }

  /**
   * Runs the specified action under Write Intent lock. Can be called from any thread. The action is executed immediately
   * if no write intent action is currently running, or blocked until the currently running write intent action completes.
   * <p>
   * This method is used to implement higher-level API, please do not use it directly.
   * Use {@link #invokeLaterOnWriteThread}, {@link com.intellij.openapi.application.WriteThread} or {@link com.intellij.openapi.application.AppUIExecutor#onWriteThread()} to
   * run code under Write Intent lock asynchronously.
   *
   * @param action the action to run
   */
  @ApiStatus.Internal
  default void runIntendedWriteActionOnCurrentThread(@NotNull Runnable action) {
    action.run();
  }
}