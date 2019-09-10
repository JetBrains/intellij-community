// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.*;

import javax.swing.*;

/**
 * @author max
 */
public interface ApplicationEx extends Application {
  String LOCATOR_FILE_NAME = ".home";

  /**
   * Loads the application configuration from the specified path
   *
   * @param configPath Path to /config folder
   */
  void load(@Nullable String configPath);

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
   * It is guaranteed that no other read or write action is run before the process start running.
   * If the process is running for too long, a progress window shown with {@code progressTitle} and a button with {@code cancelText}.
   * This method can be called from the EDT only.
   * @return true if process run successfully and was not canceled.
   */
  boolean runProcessWithProgressSynchronouslyInReadAction(@Nullable Project project,
                                                          @NotNull String progressTitle,
                                                          boolean canBeCanceled,
                                                          String cancelText,
                                                          JComponent parentComponent,
                                                          @NotNull Runnable process);

  /**
   * @param force if true, no additional confirmations will be shown. The application is guaranteed to exit
   * @param exitConfirmed if true, suppresses any shutdown confirmation. However, if there are any background processes or tasks running,
   *                      a corresponding confirmation will be shown with the possibility to cancel the operation
   */
  void exit(boolean force, boolean exitConfirmed);

  /**
   * @param exitConfirmed if true, suppresses any shutdown confirmation. However, if there are any background processes or tasks running,
   *                      a corresponding confirmation will be shown with the possibility to cancel the operation
   */
  void restart(boolean exitConfirmed);

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
  boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                              @NotNull String progressTitle,
                                              boolean canBeCanceled,
                                              Project project);

  /**
   * Runs modal process. For internal use only, see {@link Task}
   */
  @ApiStatus.Internal
  boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                              @NotNull String progressTitle,
                                              boolean canBeCanceled,
                                              @Nullable Project project,
                                              JComponent parentComponent);

  /**
   * Runs modal process. For internal use only, see {@link Task}
   */
  @ApiStatus.Internal
  boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                              @NotNull String progressTitle,
                                              boolean canBeCanceled,
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

  @TestOnly
  default void setDisposeInProgress(boolean disposeInProgress) {
  }
}