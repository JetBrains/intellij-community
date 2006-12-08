package com.intellij.debugger.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.jdi.VMDisconnectedException;

import java.util.concurrent.*;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Mar 19, 2004
 * Time: 11:42:03 AM
 * To change this template use File | Settings | File Templates.
 */
public abstract class InvokeThread<E> {
  private static final int RESTART_TIMEOUT = 500;
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.InvokeThread");
  private final String myWorkerThreadName;

  private static ThreadLocal<WorkerThreadRequest> ourWorkerRequest = new ThreadLocal<WorkerThreadRequest>();

  public abstract static class WorkerThreadRequest<E> implements Runnable {
    private final InvokeThread<E> myOwner;
    private volatile Future<?> myRequestFuture;

    protected WorkerThreadRequest(InvokeThread<E> owner) {
      myOwner = owner;
    }

    public void interrupt() {
      assert myRequestFuture != null;

      myRequestFuture.cancel( true );  
    }

    public boolean isInterrupted() {
      assert myRequestFuture != null;
      return myRequestFuture.isCancelled();
    }

    public void join() throws InterruptedException, ExecutionException {
      assert myRequestFuture != null;
      try {
        myRequestFuture.get();
      } catch(CancellationException ex) {
      }
    }

    public void join(long timeout) throws InterruptedException, ExecutionException {
      assert myRequestFuture != null;
      try {
        myRequestFuture.get(timeout, TimeUnit.MILLISECONDS);
      }
      catch (TimeoutException e) {
        return;
      } catch (CancellationException e) {
        return;
      }
    }

    final void setRequestFuture(Future<?> requestFuture) {
      myRequestFuture = requestFuture;
    }

    public InvokeThread<E> getOwner() {
      return myOwner;
    }

    public boolean isDone() {
      assert myRequestFuture != null;
      return myRequestFuture.isDone();
    }

    protected void beforeRun() {
      int retry = 0;
      while (myRequestFuture == null && retry < 20) {
        try {
          Thread.sleep(100);
          ++retry;
        }
        catch (InterruptedException e) {
        }
      }
      assert myRequestFuture != null;
      ourWorkerRequest.set(this);
    }

    protected void afterRun() {
      ourWorkerRequest.set(null);
      boolean b = Thread.interrupted(); // reset interrupted status to return into pool
    }
  }

  protected final EventQueue<E> myEvents;

  private volatile WorkerThreadRequest myCurrentRequest = null;

  public InvokeThread(String name, int countPriorites) {
    myEvents = new EventQueue<E>(countPriorites);
    myWorkerThreadName = name;
    startNewWorkerThread();
  }

  protected abstract void processEvent(E e);

  protected void startNewWorkerThread() {
    final WorkerThreadRequest workerRequest = new WorkerThreadRequest(this) {
      public void run() {
         beforeRun();
         try {
           InvokeThread.this.run(this);
         } finally {
           afterRun();
         }
      }
    };
    myCurrentRequest = workerRequest;
    workerRequest.setRequestFuture( ApplicationManager.getApplication().executeOnPooledThread(workerRequest) );
  }

  protected void restartWorkerThread() {
    getCurrentRequest().interrupt();
    try {
      getCurrentRequest().join(RESTART_TIMEOUT);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    startNewWorkerThread();
  }

  public void run(WorkerThreadRequest current) {

    for(;;) {
      try {
        if(current.isInterrupted()) {
          break;
        }

        if(getCurrentRequest() != current) {
          LOG.assertTrue(false, "Expected " + current + " instead of " + getCurrentRequest());
        }

        processEvent(myEvents.get());
      }
      catch (VMDisconnectedException e) {
        break;
      }
      catch (EventQueueClosedException e) {
        break;
      }
      catch (RuntimeException e) {
        if(e.getCause() instanceof InterruptedException) {
          break;
        }
        LOG.error(e);
      }
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Request " + this.toString() + " exited");
    }
  }

  protected static InvokeThread currentThread() {
    final WorkerThreadRequest request = getCurrentThreadRequest();
    return request != null? request.getOwner() : null;
  }

  public void invokeLater(E r, int priority) {
    if(LOG.isDebugEnabled()) {
      LOG.debug("invokeLater " + r + " in " + this);
    }
    myEvents.put(r, priority);
  }

  protected void switchToRequest(WorkerThreadRequest newWorkerThread) {
    final WorkerThreadRequest request = getCurrentThreadRequest();
    LOG.assertTrue(request != null);
    myCurrentRequest = newWorkerThread;
    if (LOG.isDebugEnabled()) {
      LOG.debug("Closing " + request + " new request = " + newWorkerThread);
    }

    request.interrupt();
  }

  public WorkerThreadRequest getCurrentRequest() {
    return myCurrentRequest;
  }

  public static WorkerThreadRequest getCurrentThreadRequest() {
    return ourWorkerRequest.get();
  }

  public void close() {
    myEvents.close();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Closing evaluation");
    }
  }
}
