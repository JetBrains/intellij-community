// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.indexing.DumbModeAccessType;
import com.sun.jdi.VMDisconnectedException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Async;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

public abstract class InvokeThread<E extends PrioritizedTask> {
  private static final Logger LOG = Logger.getInstance(InvokeThread.class);

  private static final ThreadLocal<WorkerThreadRequest<?>> ourWorkerRequest = new ThreadLocal<>();

  public static final class WorkerThreadRequest<E extends PrioritizedTask> implements Runnable {
    private final InvokeThread<E> myOwner;
    private final ProgressIndicator myProgressIndicator = new EmptyProgressIndicator();
    private volatile Future<?> myRequestFuture;

    WorkerThreadRequest(InvokeThread<E> owner) {
      myOwner = owner;
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
        ConcurrencyUtil.runUnderThreadName("DebuggerManagerThread", () -> {
          myOwner.run(this);
        });
      }
      finally {
        ourWorkerRequest.remove();
        boolean b = Thread.interrupted(); // reset interrupted status to return into pool
      }
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
  }

  protected final EventQueue<E> myEvents;

  private volatile WorkerThreadRequest<E> myCurrentRequest = null;

  public InvokeThread() {
    myEvents = new EventQueue<>(PrioritizedTask.Priority.values().length);
    startNewWorkerThread();
  }

  protected abstract void processEvent(@NotNull E e);

  protected void startNewWorkerThread() {
    final WorkerThreadRequest<E> workerRequest = new WorkerThreadRequest<>(this);
    myCurrentRequest = workerRequest;
    workerRequest.setRequestFuture(ApplicationManager.getApplication().executeOnPooledThread(workerRequest));
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

            final WorkerThreadRequest<E> currentRequest = getCurrentRequest();
            if (currentRequest != threadRequest) {
              String message = "Expected " + threadRequest + " instead of " + currentRequest + " closed=" + myEvents.isClosed();
              LOG.error(message, new IllegalStateException(message), ThreadDumper.dumpThreadsToString());
              break; // ensure events are processed by one thread at a time
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

      LOG.debug("Request " + this + " exited");
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
    final WorkerThreadRequest currentThreadRequest = getCurrentThreadRequest();
    LOG.assertTrue(currentThreadRequest != null);
    myCurrentRequest = newRequest;
    if (LOG.isDebugEnabled()) {
      LOG.debug("Closing " + currentThreadRequest + " new request = " + newRequest);
    }

    currentThreadRequest.requestStop();
  }

  public WorkerThreadRequest<E> getCurrentRequest() {
    return myCurrentRequest;
  }

  public static WorkerThreadRequest<?> getCurrentThreadRequest() {
    return ourWorkerRequest.get();
  }

  public void close() {
    myEvents.close();
    LOG.debug("Closing evaluation");
  }
}
