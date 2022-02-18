// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsContexts.ProgressDetails;
import com.intellij.openapi.util.NlsContexts.ProgressText;
import com.intellij.openapi.util.NlsContexts.ProgressTitle;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashSet;
import java.util.Set;

public abstract class ProgressManager extends ProgressIndicatorProvider {
  static ProgressManager ourInstance = CachedSingletonsRegistry.markCachedField(ProgressManager.class);

  @NotNull
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ProgressManager getInstance() {
    ProgressManager result = ourInstance;
    if (result == null) {
      result = ApplicationManager.getApplication().getService(ProgressManager.class);
      ourInstance = result;
    }
    return result;
  }

  public abstract boolean hasProgressIndicator();
  public abstract boolean hasModalProgressIndicator();
  public abstract boolean hasUnsafeProgressIndicator();

  /**
   * Runs the given process synchronously in calling thread, associating this thread with the specified progress indicator.
   * This means that it'll be returned by {@link ProgressManager#getProgressIndicator()} inside the {@code process},
   * and {@link ProgressManager#checkCanceled()} will throw a {@link ProcessCanceledException} if the progress indicator is canceled.
   *
   * @param progress an indicator to use, {@code null} means reuse current progress
   */
  public abstract void runProcess(@NotNull Runnable process, @Nullable ProgressIndicator progress) throws ProcessCanceledException;

  /**
   * Performs the given computation synchronously in calling thread and returns its result, associating this thread with the specified progress indicator.
   * This means that it'll be returned by {@link ProgressManager#getProgressIndicator()} inside the {@code process},
   * and {@link ProgressManager#checkCanceled()} will throw a {@link ProcessCanceledException} if the progress indicator is canceled.
   *
   * @param progress an indicator to use, {@code null} means reuse current progress
   */
  public final <T> T runProcess(@NotNull Computable<T> process, ProgressIndicator progress) throws ProcessCanceledException {
    Ref<T> ref = new Ref<>();
    runProcess(() -> ref.set(process.compute()), progress);
    return ref.get();
  }

  @Override
  public abstract ProgressIndicator getProgressIndicator();

  public static void progress(@NotNull @ProgressText String text) throws ProcessCanceledException {
    progress(text, "");
  }

  public static void progress2(@NotNull @ProgressDetails String text) throws ProcessCanceledException {
    final ProgressIndicator pi = getInstance().getProgressIndicator();
    if (pi != null) {
      pi.checkCanceled();
      pi.setText2(text);
    }
  }

  public static void progress(@NotNull @ProgressText String text,
                              @Nullable @ProgressDetails String text2) throws ProcessCanceledException {
    final ProgressIndicator pi = getInstance().getProgressIndicator();
    if (pi != null) {
      pi.checkCanceled();
      pi.setText(text);
      pi.setText2(text2 == null ? "" : text2);
    }
  }

  /**
   * Runs the specified operation in non-cancellable manner synchronously on the same thread where it was called.
   *
   * @see ProgressManager#computeInNonCancelableSection(ThrowableComputable)
   * @param runnable the operation to execute
   */
  public abstract void executeNonCancelableSection(@NotNull Runnable runnable);

  /**
   * Runs the specified operation and return its result in non-cancellable manner synchronously on the same thread where it was called.
   *
   * @see ProgressManager#executeNonCancelableSection(Runnable)
   * @param computable the operation to execute
   */
  public abstract <T, E extends Exception> T computeInNonCancelableSection(@NotNull ThrowableComputable<T, E> computable) throws E;

  /**
   * Runs the specified operation in a background thread and shows a modal progress dialog in the
   * main thread while the operation is executing.
   * If a dialog can't be shown (e.g. under write action or in headless environment),
   * runs the given operation synchronously in the calling thread.
   *
   * @param process       the operation to execute.
   * @param progressTitle the title of the progress window.
   * @param canBeCanceled whether "Cancel" button is shown on the progress window.
   * @param project       the project in the context of which the operation is executed.
   * @return true if the operation completed successfully, false if it was cancelled.
   */
  public abstract boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                                              @NotNull @ProgressTitle String progressTitle,
                                                              boolean canBeCanceled,
                                                              @Nullable Project project);

  /**
   * Runs the specified operation in a background thread and shows a modal progress dialog in the
   * main thread while the operation is executing.
   * If a dialog can't be shown (e.g. under write action or in headless environment),
   * runs the given operation synchronously in the calling thread.
   *
   * @param process       the operation to execute.
   * @param progressTitle the title of the progress window.
   * @param canBeCanceled whether "Cancel" button is shown on the progress window.
   * @param project       the project in the context of which the operation is executed.
   * @return true result of operation
   * @throws E exception thrown by process
   */
  public abstract <T, E extends Exception> T runProcessWithProgressSynchronously(@NotNull ThrowableComputable<T, E> process,
                                                                                 @NotNull @ProgressTitle String progressTitle,
                                                                                 boolean canBeCanceled,
                                                                                 @Nullable Project project) throws E;

  /**
   * Runs the specified operation in a background thread and shows a modal progress dialog in the
   * main thread while the operation is executing.
   * If a dialog can't be shown (e.g. under write action or in headless environment),
   * runs the given operation synchronously in the calling thread.
   *
   * @param process         the operation to execute.
   * @param progressTitle   the title of the progress window.
   * @param canBeCanceled   whether "Cancel" button is shown on the progress window.
   * @param project         the project in the context of which the operation is executed.
   * @param parentComponent the component which will be used to calculate the progress window ancestor
   * @return true if the operation completed successfully, false if it was cancelled.
   */
  public abstract boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                                              @NotNull @ProgressTitle String progressTitle,
                                                              boolean canBeCanceled,
                                                              @Nullable Project project,
                                                              @Nullable JComponent parentComponent);

  /**
   * Runs a specified {@code process} in a background thread and shows a progress dialog, which can be made non-modal by pressing
   * background button. Upon successful termination of the process a {@code successRunnable} will be called in Swing UI thread and
   * {@code canceledRunnable} will be called if terminated on behalf of the user by pressing either cancel button, while running in
   * a modal state or stop button if running in background.
   *
   * @param project          the project in the context of which the operation is executed.
   * @param progressTitle    the title of the progress window.
   * @param process          the operation to execute.
   * @param successRunnable  a callback to be called in Swing UI thread upon normal termination of the process.
   * @param canceledRunnable a callback to be called in Swing UI thread if the process have been canceled by the user.
   * @param option           progress indicator behavior controller.
   * @deprecated use {@link #run(Task)}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public abstract void runProcessWithProgressAsynchronously(@NotNull Project project,
                                                            @NotNull @ProgressTitle String progressTitle,
                                                            @NotNull Runnable process,
                                                            @Nullable Runnable successRunnable,
                                                            @Nullable Runnable canceledRunnable,
                                                            @NotNull PerformInBackgroundOption option);

  /**
   * Runs a specified {@code task} in either background/foreground thread and shows a progress dialog.
   *
   * @param task task to run (either {@link Task.Modal} or {@link Task.Backgroundable}).
   */
  public abstract void run(@NotNull Task task);

  /**
   * Runs a specified computation with a modal progress dialog.
   */
  public <T, E extends Exception> T run(@NotNull Task.WithResult<T, E> task) throws E {
    run((Task)task);
    return task.getResult();
  }

  public abstract void runProcessWithProgressAsynchronously(@NotNull Task.Backgroundable task, @NotNull ProgressIndicator progressIndicator);

  protected void indicatorCanceled(@NotNull ProgressIndicator indicator) { }

  public static void canceled(@NotNull ProgressIndicator indicator) {
    getInstance().indicatorCanceled(indicator);
  }

  /**
   * @throws ProcessCanceledException if the progress indicator associated with the current thread has been canceled.
   * @see ProgressIndicator#checkCanceled()
   */
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static void checkCanceled() throws ProcessCanceledException {
    ProgressManager instance = ourInstance;
    if (instance != null) {
      instance.doCheckCanceled();
    }
  }

  /**
   * @param progress an indicator to use, {@code null} means reuse current progress
   */
  public abstract void executeProcessUnderProgress(@NotNull Runnable process, @Nullable ProgressIndicator progress) throws ProcessCanceledException;

  public static void assertNotCircular(@NotNull ProgressIndicator original) {
    Set<ProgressIndicator> wrappedParents = null;
    for (ProgressIndicator i = original; i instanceof WrappedProgressIndicator; i = ((WrappedProgressIndicator)i).getOriginalProgressIndicator()) {
      if (wrappedParents == null) {
        wrappedParents = new HashSet<>();
      }
      if (!wrappedParents.add(i)) {
        throw new IllegalArgumentException(i + " wraps itself");
      }
    }
  }

  /**
   * This method attempts to run provided action synchronously in a read action, so that, if possible, it wouldn't impact any pending,
   * executing or future write actions (for this to work effectively the action should invoke {@link ProgressManager#checkCanceled()} or
   * {@link ProgressIndicator#checkCanceled()} often enough).
   * It returns {@code true} if action was executed successfully. It returns {@code false} if the action was not
   * executed successfully, i.e. if:
   * <ul>
   * <li>write action was in progress when the method was called</li>
   * <li>write action was pending when the method was called</li>
   * <li>action started to execute, but was aborted using {@link ProcessCanceledException} when some other thread initiated
   * write action</li>
   * </ul>
   * @param action the code to execute under read action
   * @param indicator progress indicator that should be cancelled if a write action is about to start. Can be null.
   */
  public abstract boolean runInReadActionWithWriteActionPriority(@NotNull final Runnable action, @Nullable ProgressIndicator indicator);

  public abstract boolean isInNonCancelableSection();

  /**
   * Performs the given computation while giving more priority to the current thread
   * (by forcing all other non-prioritized threads to sleep a bit whenever they call {@link #checkCanceled()}).<p></p>
   *
   * This is intended for relatively short (expected to be under 10 seconds) background activities that the user is waiting for
   * (e.g. code navigation), and which shouldn't be slowed down by CPU-intensive background tasks like highlighting or indexing.
   */
  @ApiStatus.Internal
  public abstract <T, E extends Throwable> T computePrioritized(@NotNull ThrowableComputable<T, E> computable) throws E;

  /**
   * Makes {@link #getProgressIndicator()} return {@code null} until the returned token is closed.
   */
  @ApiStatus.Internal
  public abstract @NotNull AccessToken silenceGlobalIndicator();
}
