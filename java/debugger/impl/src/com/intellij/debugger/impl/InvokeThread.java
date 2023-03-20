// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.util.ConcurrencyUtil;
import com.sun.jdi.VMDisconnectedException;
import org.jetbrains.annotations.Async;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

public abstract class InvokeThread<E extends PrioritizedTask> {
  private static final Logger LOG = Logger.getInstance(InvokeThread.class);

  private static final ThreadLocal<WorkerThreadRequest> ourWorkerRequest = new ThreadLocal<>();

  protected final Project myProject;

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
        ourWorkerRequest.set(null);
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

  private volatile WorkerThreadRequest myCurrentRequest = null;

  public InvokeThread(Project project) {
    myProject = project;
    myEvents = new EventQueue<>(PrioritizedTask.Priority.values().length);
    startNewWorkerThread();
  }

  protected abstract void processEvent(E e);

  protected void startNewWorkerThread() {
    final WorkerThreadRequest<E> workerRequest = new WorkerThreadRequest<>(this);
    myCurrentRequest = workerRequest;
    workerRequest.setRequestFuture(ApplicationManager.getApplication().executeOnPooledThread(workerRequest));
  }

  private void run(final @NotNull WorkerThreadRequest threadRequest) {
    try {
      DumbService.getInstance(myProject).runWithAlternativeResolveEnabled(() -> ProgressManager.getInstance().runProcess(() -> {
        while (true) {
          try {
            if (threadRequest.isStopRequested()) {
              break;
            }

            final WorkerThreadRequest currentRequest = getCurrentRequest();
            if (currentRequest != threadRequest) {
              String message = "Expected " + threadRequest + " instead of " + currentRequest + " closed=" + myEvents.isClosed();
              reportCommandError(new IllegalStateException(message));
              break; // ensure events are processed by one thread at a time
            }

            processEvent(myEvents.get());
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

  protected static InvokeThread currentThread() {
    final WorkerThreadRequest request = getCurrentThreadRequest();
    return request != null ? request.getOwner() : null;
  }

  public boolean schedule(@Async.Schedule E r) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("schedule " + r + " in " + this);
    }
    return myEvents.put(r, r.getPriority().ordinal());
  }

  public boolean pushBack(E r) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("pushBack " + r + " in " + this);
    }
    return myEvents.pushBack(r, r.getPriority().ordinal());
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

  public WorkerThreadRequest getCurrentRequest() {
    return myCurrentRequest;
  }

  public static WorkerThreadRequest getCurrentThreadRequest() {
    return ourWorkerRequest.get();
  }

  public void close() {
    myEvents.close();
    LOG.debug("Closing evaluation");
  }
}
