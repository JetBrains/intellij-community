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
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;

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

  void load() throws IOException;

  boolean isLoaded();

  @NotNull
  String getName();

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

  void doNotSave();
  void doNotSave(boolean value);
  boolean isDoNotSave();

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
   * Runs modal process. For internal use only, see {@link Task}
   */
  boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                              @NotNull String progressTitle,
                                              boolean canBeCanceled,
                                              Project project);

  /**
   * Runs modal process. For internal use only, see {@link Task}
   */
  boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                              @NotNull String progressTitle,
                                              boolean canBeCanceled,
                                              @Nullable Project project,
                                              JComponent parentComponent);

  /**
   * Runs modal process. For internal use only, see {@link Task}
   */
  boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                              @NotNull String progressTitle,
                                              boolean canBeCanceled,
                                              @Nullable Project project,
                                              JComponent parentComponent,
                                              final String cancelText);

  void assertIsDispatchThread(@Nullable JComponent component);

  void assertTimeConsuming();

  void runEdtSafeAction(@NotNull Runnable runnable);

  /**
   * Tries to acquire the read lock and run the {@code action}
   *
   * @return true if action was run while holding the lock, false if was unable to get the lock and action was not run
   */
  boolean tryRunReadAction(@NotNull Runnable action);
}
