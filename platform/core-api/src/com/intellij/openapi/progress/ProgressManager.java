/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.progress;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class ProgressManager {
  private static final ProgressManager ourInstance = ServiceManager.getService(ProgressManager.class);

  static {
    ProgressIndicatorProvider.ourInstance = new ProgressIndicatorProvider() {
      @Override
      public ProgressIndicator getProgressIndicator() {
        return ProgressManager.ourInstance.getProgressIndicator();
      }

      @Override
      protected void doCheckCanceled() throws ProcessCanceledException {
        ProgressManager.ourInstance.doCheckCanceled();
      }


      @Override
      public NonCancelableSection startNonCancelableSection() {
        return ProgressManager.ourInstance.startNonCancelableSection();
      }
    };
  }

  public static ProgressManager getInstance() {
    return ourInstance;
  }

  public abstract boolean hasProgressIndicator();
  public abstract boolean hasModalProgressIndicator();
  public abstract boolean hasUnsafeProgressIndicator();

  public abstract void runProcess(@NotNull Runnable process, ProgressIndicator progress) throws ProcessCanceledException;
  public abstract <T> T runProcess(@NotNull Computable<T> process, ProgressIndicator progress) throws ProcessCanceledException;

  public ProgressIndicator getProgressIndicator() {
    return myThreadIndicator.get();
  }

  protected static volatile boolean ourNeedToCheckCancel = false;
  public static void checkCanceled() throws ProcessCanceledException {
    // smart optimization! There's a thread started in ProgressManagerImpl, that set's this flag up once in 10 milliseconds
    if (ourNeedToCheckCancel) {
      getInstance().doCheckCanceled();
      ourNeedToCheckCancel = false;
    }
  }

  public static void progress(final String text) throws ProcessCanceledException {
    progress(text, "");
  }

  public static void progress2(@NotNull final String text) throws ProcessCanceledException {
    final ProgressIndicator pi = getInstance().getProgressIndicator();
    if (pi != null) {
      pi.checkCanceled();
      pi.setText2(text);
    }
  }

  public static void progress(final String text, @Nullable String text2) throws ProcessCanceledException {
    final ProgressIndicator pi = getInstance().getProgressIndicator();
    if (pi != null) {
      pi.checkCanceled();
      pi.setText(text);
      pi.setText2(text2 == null ? "" : text2);
    }
  }

  protected abstract void doCheckCanceled() throws ProcessCanceledException;

  public abstract void executeNonCancelableSection(@NotNull Runnable runnable);
  public abstract NonCancelableSection startNonCancelableSection();

  public abstract void setCancelButtonText(String cancelButtonText);


  /**
   * Runs the specified operation in a background thread and shows a modal progress dialog in the
   * main thread while the operation is executing.
   *
   * @param process       the operation to execute.
   * @param progressTitle the title of the progress window.
   * @param canBeCanceled whether "Cancel" button is shown on the progress window.
   * @param project       the project in the context of which the operation is executed.
   * @return true if the operation completed successfully, false if it was cancelled.
   */
  public abstract boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                                              @NotNull @Nls String progressTitle,
                                                              boolean canBeCanceled,
                                                              @Nullable Project project);

  /**
   * Runs the specified operation in a background thread and shows a modal progress dialog in the
   * main thread while the operation is executing.
   *
   * @param process       the operation to execute.
   * @param progressTitle the title of the progress window.
   * @param canBeCanceled whether "Cancel" button is shown on the progress window.
   * @param project       the project in the context of which the operation is executed.
   * @return true result of operation
   * @throws E exception thrown by process
   */
  public abstract <T, E extends Exception> T runProcessWithProgressSynchronously(@NotNull ThrowableComputable<T, E> process,
                                                              @NotNull @Nls String progressTitle,
                                                              boolean canBeCanceled,
                                                              @Nullable Project project) throws E;

  /**
   * Runs the specified operation in a background thread and shows a modal progress dialog in the
   * main thread while the operation is executing.
   *
   * @param process         the operation to execute.
   * @param progressTitle   the title of the progress window.
   * @param canBeCanceled   whether "Cancel" button is shown on the progress window.
   * @param project         the project in the context of which the operation is executed.
   * @param parentComponent the component which will be used to calculate the progress window ancestor
   * @return true if the operation completed successfully, false if it was cancelled.
   */
  public abstract boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                                              @NotNull @Nls String progressTitle,
                                                              boolean canBeCanceled,
                                                              @Nullable Project project,
                                                              @Nullable JComponent parentComponent);

  /**
   * Runs a specified <code>process</code> in a background thread and shows a progress dialog, which can be made non-modal by pressing
   * background button. Upon successful termination of the process a <code>successRunnable</code> will be called in Swing UI thread and
   * <code>canceledRunnable</code> will be called if terminated on behalf of the user by pressing either cancel button, while running in
   * a modal state or stop button if running in background.
   *
   * @param project          the project in the context of which the operation is executed.
   * @param progressTitle    the title of the progress window.
   * @param process          the operation to execute.
   * @param successRunnable  a callback to be called in Swing UI thread upon normal termination of the process.
   * @param canceledRunnable a callback to be called in Swing UI thread if the process have been canceled by the user.
   * @deprecated use {@link #run(com.intellij.openapi.progress.Task)}
   */
  public abstract void runProcessWithProgressAsynchronously(@NotNull Project project,
                                                            @NotNull @Nls String progressTitle,
                                                            @NotNull Runnable process,
                                                            @Nullable Runnable successRunnable,
                                                            @Nullable Runnable canceledRunnable);
  /**
   * Runs a specified <code>process</code> in a background thread and shows a progress dialog, which can be made non-modal by pressing
   * background button. Upon successful termination of the process a <code>successRunnable</code> will be called in Swing UI thread and
   * <code>canceledRunnable</code> will be called if terminated on behalf of the user by pressing either cancel button, while running in
   * a modal state or stop button if running in background.
   *
   * @param project          the project in the context of which the operation is executed.
   * @param progressTitle    the title of the progress window.
   * @param process          the operation to execute.
   * @param successRunnable  a callback to be called in Swing UI thread upon normal termination of the process.
   * @param canceledRunnable a callback to be called in Swing UI thread if the process have been canceled by the user.
   * @param option           progress indicator behavior controller.
   * @deprecated use {@link #run(com.intellij.openapi.progress.Task)}
   */
  public abstract void runProcessWithProgressAsynchronously(@NotNull Project project,
                                                            @NotNull @Nls String progressTitle,
                                                            @NotNull Runnable process,
                                                            @Nullable Runnable successRunnable,
                                                            @Nullable Runnable canceledRunnable,
                                                            @NotNull PerformInBackgroundOption option);

  /**
   * Runs a specified <code>task</code> in either background/foreground thread and shows a progress dialog.
   * @param task          task to run (either {@link com.intellij.openapi.progress.Task.Modal} or {@link com.intellij.openapi.progress.Task.Backgroundable}).
   */
  public abstract void run(@NotNull Task task);

  public abstract void runProcessWithProgressAsynchronously(@NotNull Task.Backgroundable task, @NotNull ProgressIndicator progressIndicator);

  protected static final ThreadLocal<ProgressIndicator> myThreadIndicator = new ThreadLocal<ProgressIndicator>();
  public void executeProcessUnderProgress(@NotNull Runnable process, ProgressIndicator progress) throws ProcessCanceledException {
    ProgressIndicator oldIndicator = null;

    boolean set = progress != null && progress != (oldIndicator = myThreadIndicator.get());
    if (set) {
      myThreadIndicator.set(progress);
    }

    try {
      process.run();
    }
    finally {
      if (set) {
        myThreadIndicator.set(oldIndicator);
      }
    }
  }
}