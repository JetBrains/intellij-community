// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.concurrency.ContextAwareRunnable;
import com.intellij.concurrency.ThreadContext;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.concurrency.BlockingJob;
import com.intellij.util.indexing.DumbModeAccessType;
import com.sun.jdi.VMDisconnectedException;
import kotlin.Unit;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.CompletableJob;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.JobKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Async;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class InvokeThread<E extends PrioritizedTask> {
  private static final Logger LOG = Logger.getInstance(InvokeThread.class);

  private static final ThreadLocal<WorkerThreadRequest<?>> ourWorkerRequest = new ThreadLocal<>();
  private static final AtomicInteger ourWorkerCounter = new AtomicInteger(1);

  public static final class WorkerThreadRequest<E extends PrioritizedTask> implements ContextAwareRunnable {
    private final InvokeThread<E> myOwner;
    private final Job myWorkJob;
    // keeps the work job in the completing state while this worker lives, so drain-time spawns still attach
    private final CompletableJob myLifetimeJob;
    private final ProgressIndicator myProgressIndicator = new EmptyProgressIndicator();
    private volatile Future<?> myRequestFuture;
    private final int myId = ourWorkerCounter.getAndIncrement();

    WorkerThreadRequest(InvokeThread<E> owner, @NotNull Job workJob) {
      myOwner = owner;
      myWorkJob = workJob;
      myLifetimeJob = JobKt.Job(workJob);
    }

    @Override
    public void run() {
      synchronized (this) {
        while (myRequestFuture == null) {
          try {
            wait();
          }
          catch (InterruptedException ignore) {
          }
        }
      }
      ourWorkerRequest.set(this);
      try {
        ThreadContext.installThreadContext(workerContext(), true, () -> {
          ConcurrencyUtil.runUnderThreadName("DebuggerManagerThread", () -> {
            myOwner.run(this);
          });
          return Unit.INSTANCE;
        });
      }
      finally {
        ourWorkerRequest.remove();
        myLifetimeJob.complete();
        boolean b = Thread.interrupted(); // reset interrupted status to return into pool
      }
    }

    // spawned work must attach to the owner's work job, never to the thread that started the worker
    private @NotNull CoroutineContext workerContext() {
      return ambientContextWithoutJobs().plus(new BlockingJob(myWorkJob));
    }

    public void requestStop() {
      final Future<?> future = myRequestFuture;
      assert future != null;
      myProgressIndicator.cancel();
      future.cancel(true);
    }

    public boolean isStopRequested() {
      final Future<?> future = myRequestFuture;
      assert future != null;
      return myProgressIndicator.isCanceled() || future.isCancelled() || future.isDone();
    }

    @ApiStatus.Internal
    public @NotNull ProgressIndicator getProgressIndicator() {
      return myProgressIndicator;
    }

    public void join() throws InterruptedException, ExecutionException {
      assert myRequestFuture != null;
      try {
        myRequestFuture.get();
      }
      catch (CancellationException ignored) {
      }
    }

    public void join(long timeout) throws InterruptedException, ExecutionException {
      assert myRequestFuture != null;
      try {
        myRequestFuture.get(timeout, TimeUnit.MILLISECONDS);
      }
      catch (TimeoutException | CancellationException ignored) {
      }
    }

    void setRequestFuture(Future<?> requestFuture) {
      synchronized (this) {
        myRequestFuture = requestFuture;
        notifyAll();
      }
    }

    public InvokeThread<E> getOwner() {
      return myOwner;
    }

    public boolean isDone() {
      assert myRequestFuture != null;
      return myRequestFuture.isDone() && ourWorkerRequest.get() == null;
    }

    @Override
    public String toString() {
      return String.valueOf(myId);
    }
  }

  protected final EventQueue<E> myEvents;

  private WorkerThreadRequest<E> myCurrentRequest = null;

  /** The subclass must call {@link #startNewWorkerThread()} when its state is ready for {@link #getWorkJob()}. */
  public InvokeThread() {
    myEvents = new EventQueue<>(PrioritizedTask.Priority.values().length);
  }

  protected abstract void processEvent(@NotNull E e);

  /**
   * The job that owns work spawned from a worker; read again on each worker start.
   * Each worker holds a child of it for its lifetime, so the job completes only after every worker has exited
   * and all spawned work has finished.
   */
  protected abstract @NotNull Job getWorkJob();

  private static @NotNull CoroutineContext ambientContextWithoutJobs() {
    return ThreadContext.currentThreadContext().minusKey(Job.Key).minusKey(BlockingJob.Companion);
  }

  protected void startNewWorkerThread() {
    // myCurrentRequest has to be updated atomically with calling setRequestFuture
    // otherwise we may have asserts triggering inside workerRequest.requestStop etc.
    synchronized (this) {
      assertCurrentThreadIsActive();

      final WorkerThreadRequest<E> workerRequest = new WorkerThreadRequest<>(this, getWorkJob());
      WorkerThreadRequest<E> oldRequest = myCurrentRequest; // just for logging
      myCurrentRequest = workerRequest;
      if (LOG.isDebugEnabled()) {
        LOG.debug("Started new worker thread request " + workerRequest + ", was " + oldRequest);
      }
      // the executor captures the submitter context for the worker's whole lifetime; keep the jobs out of it
      ThreadContext.installThreadContext(ambientContextWithoutJobs(), true, () -> {
        workerRequest.setRequestFuture(ApplicationManager.getApplication().executeOnPooledThread(workerRequest));
        return Unit.INSTANCE;
      });
    }
  }

  protected static boolean assertCurrentThreadIsActive() {
    InvokeThread<?> thread = currentThread();
    if (thread == null) {
      return true;
    }
    WorkerThreadRequest<?> currentRequest = thread.getCurrentRequest();
    WorkerThreadRequest<?> threadRequest = getCurrentThreadRequest();
    if (currentRequest != threadRequest) {
      String message =
        "Expected worker request " + threadRequest + " instead of " + currentRequest + " closed=" + thread.myEvents.isClosed();
      reportCommandError(new IllegalStateException(message)); // do not throw AssertionError in tests, we need to return false here
      return false;
    }
    return true;
  }

  // Extracted to have a separate method for @Async.Execute
  private void doProcessEvent(@Async.Execute E event) {
    processEvent(event);
  }

  private void run(final @NotNull WorkerThreadRequest<?> threadRequest) {
    try {
      DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> ProgressManager.getInstance().runProcess(() -> {
        while (true) {
          try {
            if (threadRequest.isStopRequested()) {
              break;
            }

            if (!assertCurrentThreadIsActive()) {
              break;
            }

            doProcessEvent(myEvents.get());
          }
          catch (VMDisconnectedException | EventQueueClosedException ignored) {
            break;
          }
          catch (ProcessCanceledException ignored) {
          }
          catch (CompletionException e) {
            if (e.getCause() instanceof VMDisconnectedException) {
              break;
            }
            reportCommandError(e);
          }
          catch (RuntimeException e) {
            if (e.getCause() instanceof InterruptedException) {
              break;
            }
            reportCommandError(e);
          }
          catch (Throwable e) {
            reportCommandError(e);
          }
        }
      }, threadRequest.myProgressIndicator));
    }
    finally {
      // ensure that all scheduled events are processed
      if (threadRequest == getCurrentRequest()) {
        processRemaining();
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug("Request " + threadRequest + " exited");
      }
    }
  }

  public void processRemaining() {
    for (E event : myEvents.clearQueue()) {
      try {
        processEvent(event);
      }
      catch (Throwable ignored) {
      }
    }
  }

  private static void reportCommandError(Throwable e) {
    try {
      LOG.error(e);
    }
    catch (AssertionError ignored) {
      //do not destroy commands processing
    }
  }

  public static InvokeThread<?> currentThread() {
    final WorkerThreadRequest<?> request = getCurrentThreadRequest();
    return request != null ? request.getOwner() : null;
  }

  public boolean schedule(@NotNull @Async.Schedule E r) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("schedule " + r + " in " + this);
    }
    setCommandManagerThread(r);
    return myEvents.put(r, r.getPriority().ordinal());
  }

  public boolean pushBack(@NotNull E r) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("pushBack " + r + " in " + this);
    }
    setCommandManagerThread(r);
    return myEvents.pushBack(r, r.getPriority().ordinal());
  }

  @ApiStatus.Internal
  public void setCommandManagerThread(E event) {
    if (event instanceof DebuggerCommandImpl command) {
      command.setCommandManagerThread$intellij_java_debugger_impl((DebuggerManagerThreadImpl)this);
    }
  }

  protected void switchToRequest(WorkerThreadRequest newRequest) {
    WorkerThreadRequest currentThreadRequest;
    synchronized (this) {
      currentThreadRequest = getCurrentThreadRequest();
      LOG.assertTrue(currentThreadRequest != null);
      myCurrentRequest = newRequest;
      if (LOG.isDebugEnabled()) {
        LOG.debug("Switched current request from " + currentThreadRequest + " to " + newRequest);
      }
    }

    currentThreadRequest.requestStop();
  }

  public WorkerThreadRequest<E> getCurrentRequest() {
    synchronized (this) {
      return myCurrentRequest;
    }
  }

  public static WorkerThreadRequest<?> getCurrentThreadRequest() {
    return ourWorkerRequest.get();
  }

  public void close() {
    myEvents.close();
    LOG.debug("Closing evaluation");
  }
}
