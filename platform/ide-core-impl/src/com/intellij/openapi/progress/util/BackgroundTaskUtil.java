// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util;

import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairConsumer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class BackgroundTaskUtil {
  private static final Logger LOG = Logger.getInstance(BackgroundTaskUtil.class);

  @RequiresEdt
  public static @NotNull ProgressIndicator executeAndTryWait(@NotNull Function<? super ProgressIndicator, /*@NotNull*/ ? extends Runnable> backgroundTask,
                                                             @Nullable Runnable onSlowAction) {
    return executeAndTryWait(backgroundTask, onSlowAction,
                             ProgressIndicatorWithDelayedPresentation.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS, false);
  }

  /**
   * Executor to perform <i>possibly</i> long operation on pooled thread.
   * If computation was performed within given time frame,
   * the computed callback will be executed synchronously (avoiding unnecessary <tt>invokeLater()</tt>).
   * In this case, {@code onSlowAction} will not be executed at all.
   * <ul>
   * <li> If the computation is fast, execute callback synchronously.
   * <li> If the computation is slow, execute <tt>onSlowAction</tt> synchronously. When the computation is completed, execute callback in EDT.
   * </ul><p>
   * It can be used to reduce blinking when background task might be completed fast.<br>
   * A Simple approach:
   * <pre>
   * onSlowAction.run() // show "Loading..."
   * executeOnPooledThread({
   *   Runnable callback = backgroundTask(); // some background computations
   *   invokeLater(callback); // apply changes
   * });
   * </pre>
   * will lead to "Loading..." visible between current moment and execution of invokeLater() event.
   * This period can be very short and looks like 'jumping' if background operation is fast.
   */
  @RequiresEdt
  public static @NotNull ProgressIndicator executeAndTryWait(@NotNull Function<? super ProgressIndicator, /*@NotNull*/ ? extends Runnable> backgroundTask,
                                                             @Nullable Runnable onSlowAction,
                                                             long waitMillis,
                                                             boolean forceEDT) {
    ModalityState modality = ModalityState.current();

    if (forceEDT) {
      ProgressIndicator indicator = new EmptyProgressIndicator(modality);
      try {
        Runnable callback = ProgressManager.getInstance().runProcess(() -> backgroundTask.fun(indicator), indicator);
        finish(callback, indicator);
      }
      catch (ProcessCanceledException ignore) {
      }
      catch (Throwable t) {
        LOG.error(t);
      }
      return indicator;
    }
    else {
      Pair<Runnable, ProgressIndicator> pair = computeInBackgroundAndTryWait(
        backgroundTask,
        (callback, indicator) -> ApplicationManager.getApplication().invokeLater(() -> finish(callback, indicator), modality),
        modality,
        waitMillis);

      Runnable callback = pair.first;
      ProgressIndicator indicator = pair.second;

      if (callback != null) {
        finish(callback, indicator);
      }
      else {
        if (onSlowAction != null) onSlowAction.run();
      }

      return indicator;
    }
  }

  @RequiresEdt
  private static void finish(@NotNull Runnable result, @NotNull ProgressIndicator indicator) {
    if (!indicator.isCanceled()) result.run();
  }

  /**
   * Try to compute value in background and abort computation if it takes too long.
   * <ul>
   * <li> If the computation is fast, return computed value.
   * <li> If the computation is slow, abort computation (cancel ProgressIndicator).
   * </ul>
   */
  @CalledInAny
  public static @Nullable <T> T tryComputeFast(@NotNull Function<? super ProgressIndicator, ? extends T> backgroundTask, long waitMillis) {
    Pair<T, ProgressIndicator> pair = computeInBackgroundAndTryWait(
      backgroundTask,
      (result, indicator) -> {
      },
      ModalityState.defaultModalityState(),
      waitMillis);

    T result = pair.first;
    ProgressIndicator indicator = pair.second;

    indicator.cancel();
    return result;
  }

  @CalledInAny
  public static @Nullable <T> T computeInBackgroundAndTryWait(@NotNull Computable<? extends T> computable,
                                                              @NotNull Consumer<? super T> asyncCallback,
                                                              long waitMillis) {
    Pair<T, ProgressIndicator> pair = computeInBackgroundAndTryWait(
      indicator -> computable.compute(),
      (result, indicator) -> asyncCallback.consume(result),
      ModalityState.defaultModalityState(),
      waitMillis
    );
    return pair.first;
  }

  /**
   * Compute value in background and try wait for its completion.
   * <ul>
   * <li> If the computation is fast, return computed value synchronously. Callback will not be called in this case.
   * <li> If the computation is slow, return <tt>null</tt>. When the computation is completed, pass the value to the callback.
   * </ul>
   * Callback will be executed on the same thread as the background task.
   */
  @CalledInAny
  private static @NotNull <T> Pair<T, ProgressIndicator> computeInBackgroundAndTryWait(@NotNull Function<? super ProgressIndicator, ? extends T> task,
                                                                                       @NotNull PairConsumer<? super T, ? super ProgressIndicator> asyncCallback,
                                                                                       @NotNull ModalityState modality,
                                                                                       long waitMillis) {
    ProgressIndicator indicator = new EmptyProgressIndicator(modality);
    indicator.start();

    Helper<T> helper = new Helper<>();

    ApplicationManager.getApplication().executeOnPooledThread(() -> ProgressManager.getInstance().runProcess(() -> {
      T result = task.fun(indicator);
      if (!helper.setResult(result)) {
        asyncCallback.consume(result, indicator);
      }
    }, indicator));

    T result = null;
    if (helper.await(waitMillis)) {
      result = helper.getResult();
    }

    return Pair.create(result, indicator);
  }

  public static final class BackgroundTask<T> {
    private final @NotNull Disposable parent;
    private final @NotNull ProgressIndicator indicator;
    private final @NotNull CompletableFuture<T> future;

    public BackgroundTask(@NotNull Disposable parent,
                          @NotNull ProgressIndicator indicator,
                          @NotNull CompletableFuture<T> future) {
      this.parent = parent;
      this.indicator = indicator;
      this.future = future;
    }

    public @NotNull Disposable getParent() {
      return parent;
    }

    public @NotNull ProgressIndicator getIndicator() {
      return indicator;
    }

    public @NotNull CompletableFuture<T> getFuture() {
      return future;
    }

    public void cancel() {
      indicator.cancel();
    }

    public void awaitCompletion() throws ExecutionException {
      while (!future.isDone() && !Disposer.isDisposed(parent)) {
        try {
          if (future.get(1, TimeUnit.SECONDS) != null) {
            break;
          }
        }
        catch (ExecutionException e) {
          if (e.getCause() instanceof ControlFlowException) {
            break;
          }
          throw e;
        }
        catch (TimeoutException e) {
          // another
        }
        catch (Exception e) {
          if (e.getCause() instanceof ControlFlowException) {
            break;
          }
          throw new RuntimeException(e);
        }
      }
    }
  }

  /**
   * An alternative to plain {@link Application#executeOnPooledThread(Runnable)} which wraps the task in a process with a
   * {@link ProgressIndicator} which gets cancelled when the given disposable is disposed. <br/><br/>
   * <p>
   * This allows to stop a lengthy background activity by calling {@link ProgressManager#checkCanceled()}
   * and avoid Already Disposed exceptions (in particular, because checkCanceled() is called in {@link ComponentManager#getService(Class)}.
   */
  @CalledInAny
  public static @NotNull ProgressIndicator executeOnPooledThread(@NotNull Disposable parent, @NotNull Runnable runnable) {
    return execute(AppExecutorUtil.getAppExecutorService(), parent, runnable);
  }

  /**
   * Does tha same as {@link BackgroundTaskUtil#executeOnPooledThread(Disposable, Runnable)} method but allows to use
   * custom {@link Executor} instance.
   */
  @CalledInAny
  public static @NotNull ProgressIndicator execute(@NotNull Executor executor, @NotNull Disposable parent, @NotNull Runnable runnable) {
    return submitTask(executor, parent, runnable).indicator;
  }

  @CalledInAny
  public static @NotNull BackgroundTask<?> submitTask(@NotNull Disposable parent, @NotNull Runnable runnable) {
    return submitTask(AppExecutorUtil.getAppExecutorService(), parent, runnable);
  }

  /**
   * Does tha same as {@link BackgroundTaskUtil#execute(Executor, Disposable, Runnable)} method but allows
   * to track execution {@link CompletableFuture}.
   */
  @CalledInAny
  public static @NotNull BackgroundTask<?> submitTask(@NotNull Executor executor, @NotNull Disposable parent, @NotNull Runnable task) {
    return createBackgroundTask(executor, () -> {
      task.run();
      return null;
    }, task.toString(), parent);
  }

  @CalledInAny
  public static @NotNull <T> BackgroundTask<T> submitTask(@NotNull Executor executor,
                                                          @NotNull Disposable parent,
                                                          @NotNull Computable<? extends T> task) {
    return createBackgroundTask(executor, task, task.toString(), parent);
  }

  private static @NotNull <T> BackgroundTask<T> createBackgroundTask(@NotNull Executor executor,
                                                                     @NotNull Computable<? extends T> task,
                                                                     @NotNull @NlsSafe String taskName,
                                                                     @NotNull Disposable parent) {
    ProgressIndicator indicator = new EmptyProgressIndicator();

    AtomicReference<Future<?>> futureRef = new AtomicReference<>();
    Disposable disposable = () -> {
      if (indicator.isRunning()) {
        indicator.cancel();
      }

      Future<?> future = futureRef.get();
      if (future != null) {
        tryAwaitFuture(future, taskName); // give a task chance to finish in sync
      }
    };

    CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
      return ProgressManager.getInstance().runProcess(() -> {
        if (!registerIfParentNotDisposed(parent, disposable)) {
          throw new ProcessCanceledException();
        }

        return task.compute();
      }, indicator);
    }, executor);
    future.whenComplete((o, e) -> Disposer.dispose(disposable));
    futureRef.set(future);

    return new BackgroundTask<>(parent, indicator, future);
  }

  private static void tryAwaitFuture(@NotNull Future<?> future, @NotNull @NlsSafe String taskName) {
    try {
      future.get(1, TimeUnit.SECONDS);
    }
    catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof CancellationException) {
        // ignore: expected cancellation
      }
      else {
        LOG.error(e);
      }
    }
    catch (CancellationException ignored) {
    }
    catch (InterruptedException | TimeoutException e) {
      LOG.debug("Couldn't await background process on disposal: " + taskName);
    }
  }

  @CalledInAny
  public static <T> T runUnderDisposeAwareIndicator(@NotNull Disposable parent, @NotNull Supplier<? extends T> task) {
    return runUnderDisposeAwareIndicator(parent, task, ProgressManager.getInstance().getProgressIndicator());
  }

  @CalledInAny
  public static <T> T runUnderDisposeAwareIndicator(@NotNull Disposable parent,
                                                    @NotNull Supplier<? extends T> task,
                                                    @Nullable ProgressIndicator parentIndicator) {
    Ref<T> ref = new Ref<>();
    runUnderDisposeAwareIndicator(parent, () -> {
      ref.set(task.get());
    }, parentIndicator);
    return ref.get();
  }

  @CalledInAny
  public static void runUnderDisposeAwareIndicator(@NotNull Disposable parent, @NotNull Runnable task) {
    runUnderDisposeAwareIndicator(parent, task, ProgressManager.getInstance().getProgressIndicator());
  }

  @CalledInAny
  public static void runUnderDisposeAwareIndicator(@NotNull Disposable parent,
                                                   @NotNull Runnable task,
                                                   @Nullable ProgressIndicator parentIndicator) {
    final ProgressIndicator indicator = parentIndicator instanceof StandardProgressIndicator
                                        ? new SensitiveProgressWrapper(parentIndicator)
                                        : new EmptyProgressIndicator(ModalityState.defaultModalityState());
    Disposable disposable = () -> {
      if (indicator.isRunning()) {
        indicator.cancel();
      }
    };

    try {
      ProgressManager.getInstance().runProcess(() -> {
        if (!registerIfParentNotDisposed(parent, disposable)) {
          throw new ProcessCanceledException();
        }

        task.run();
      }, indicator);
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  private static boolean registerIfParentNotDisposed(@NotNull Disposable parent, @NotNull Disposable disposable) {
    if (parent instanceof ComponentManager && ((ComponentManager)parent).isDisposed()) {
      return false;
    }

    return Disposer.tryRegister(parent, disposable);
  }

  /**
   * Wraps {@link MessageBus#syncPublisher(Topic)} in a dispose check,
   * and throws a {@link ProcessCanceledException} if the project is disposed,
   * instead of throwing an assertion which would happen otherwise.
   *
   * @see #syncPublisher(Topic)
   */
  @CalledInAny
  public static @NotNull <L> L syncPublisher(@NotNull Project project, @NotNull Topic<L> topic) throws ProcessCanceledException {
    return project.getMessageBus().syncPublisher(topic);
  }

  /**
   * Wraps {@link MessageBus#syncPublisher(Topic)} in a dispose check,
   * and throws a {@link ProcessCanceledException} if the application is disposed,
   * instead of throwing an assertion which would happen otherwise.
   *
   * @see #syncPublisher(Project, Topic)
   */
  @CalledInAny
  public static @NotNull <L> L syncPublisher(@NotNull Topic<L> topic) throws ProcessCanceledException {
    return ReadAction.compute(() -> {
      if (ApplicationManager.getApplication().isDisposed()) throw new ProcessCanceledException();
      return ApplicationManager.getApplication().getMessageBus().syncPublisher(topic);
    });
  }

  private static final class Helper<T> {
    private static final Object INITIAL_STATE = ObjectUtils.sentinel("INITIAL_STATE");
    private static final Object SLOW_OPERATION_STATE = ObjectUtils.sentinel("SLOW_OPERATION_STATE");

    private final Semaphore mySemaphore = new Semaphore(0);
    private final AtomicReference<Object> myResultRef = new AtomicReference<>(INITIAL_STATE);

    /**
     * @return true if computation was fast, and callback should be handled by other thread
     */
    public boolean setResult(T result) {
      boolean isFast = myResultRef.compareAndSet(INITIAL_STATE, result);
      mySemaphore.release();
      return isFast;
    }

    /**
     * @return true if computation was fast, and callback should be handled by current thread
     */
    public boolean await(long waitMillis) {
      try {
        mySemaphore.tryAcquire(waitMillis, TimeUnit.MILLISECONDS);
      }
      catch (InterruptedException ignore) {
      }

      return !myResultRef.compareAndSet(INITIAL_STATE, SLOW_OPERATION_STATE);
    }

    public T getResult() {
      Object result = myResultRef.get();
      assert result != INITIAL_STATE && result != SLOW_OPERATION_STATE;
      //noinspection unchecked
      return (T)result;
    }
  }
}
