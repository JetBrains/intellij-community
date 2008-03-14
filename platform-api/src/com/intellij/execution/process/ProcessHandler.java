/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.execution.process;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class ProcessHandler extends UserDataHolderBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.ProcessHandler");
  private final List<ProcessListener> myListeners = new CopyOnWriteArrayList<ProcessListener>();

  private static final int STATE_INITIAL     = 0;
  private static final int STATE_STARTING    = 1;
  private static final int STATE_STARTED     = 2;
  private static final int STATE_TERMINATING = 3;
  private static final int STATE_TERMINATED  = 4;

  private int myState = STATE_INITIAL;

  private final Semaphore    myWaitSemaphore;
  private ProcessListener myEventMulticaster;

  protected ProcessHandler() {
    myEventMulticaster = createEventMulticaster();
    myWaitSemaphore = new Semaphore();
    myWaitSemaphore.down();
  }

  public void startNotify() {
    if(isStartNotified()) {
      LOG.assertTrue(false, "startNotify called already");
      return;
    }
    myState = STATE_STARTING;
    try {
      myEventMulticaster.startNotified(new ProcessEvent(this));
    }
    finally {
      myState = STATE_STARTED;
    }
  }

  protected abstract void destroyProcessImpl();

  protected abstract void detachProcessImpl();

  public abstract boolean detachIsDefault();

  public void waitFor() {
    myWaitSemaphore.waitFor();
  }

  public boolean waitFor(long timeoutInMilliseconds) {
    return myWaitSemaphore.waitFor(timeoutInMilliseconds);
  }

  public void destroyProcess() {
    afterStartNotified(new Runnable() {
      public void run() {
        if (isProcessTerminated() || isProcessTerminating()) return;
        myState = STATE_TERMINATING;
        fireProcessWillTerminate(true);
        destroyProcessImpl();
      }
    });
  }

  public void detachProcess() {
    afterStartNotified(new Runnable() {
      public void run() {
        if (isProcessTerminated() || isProcessTerminating()) return;
        myState = STATE_TERMINATING;
        fireProcessWillTerminate(false);
        detachProcessImpl();
      }
    });
  }

  public boolean isProcessTerminated() {
    return myState == STATE_TERMINATED;
  }

  public boolean isProcessTerminating() {
    return myState == STATE_TERMINATING;
  }

  public void addProcessListener(final ProcessListener listener) {
    synchronized(myListeners) {
      myListeners.add(listener);
    }
  }

  public void removeProcessListener(final ProcessListener listener) {
    synchronized(myListeners) {
      myListeners.remove(listener);
    }
  }

  protected void notifyProcessDetached() {
    notifyTerminated(0, false);
  }

  protected void notifyProcessTerminated(final int exitCode) {
    notifyTerminated(exitCode, true);
  }

  private void notifyTerminated(final int exitCode, final boolean willBeDestroyed) {
    afterStartNotified(new Runnable() {
      public void run() {
        LOG.assertTrue(isStartNotified(), "Start notify is not called");

        if (!isProcessTerminating() && !isProcessTerminated()) {
          myState = STATE_TERMINATING;
          try {
            fireProcessWillTerminate(willBeDestroyed);
          }
          catch (Throwable e) {
            LOG.error(e);
          }
        }

        LOG.assertTrue(isStartNotified(), "All events should be fired after startNotify is called");
        try {
          myEventMulticaster.processTerminated(new ProcessEvent(ProcessHandler.this, exitCode));
        }
        catch (Throwable e) {
          LOG.error(e);
        }
        finally {
          myState = STATE_TERMINATED;
          myWaitSemaphore.up();
        }
      }
    });
  }

  public void notifyTextAvailable(final String text, final Key outputType) {
    final ProcessEvent event = new ProcessEvent(this, text);
    myEventMulticaster.onTextAvailable(event, outputType);
  }

  @Nullable
  public abstract OutputStream getProcessInput();

  private void fireProcessWillTerminate(final boolean willBeDestroyed) {
    LOG.assertTrue(isStartNotified(), "All events should be fired after startNotify is called");
    myEventMulticaster.processWillTerminate(new ProcessEvent(this), willBeDestroyed);
  }

  private void afterStartNotified(final Runnable runnable) {
    if (isStartNotified()) {
      runnable.run();
    }
    else {
      addProcessListener(new ProcessAdapter() {
        public void startNotified(ProcessEvent event) {
          removeProcessListener(this);
          runnable.run();
        }
      });
    }
  }

  public boolean isStartNotified() {
    return myState > STATE_INITIAL;
  }

  private ProcessListener createEventMulticaster() {
    final Class<ProcessListener> listenerClass = ProcessListener.class;
    return (ProcessListener)Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class[] {listenerClass}, new InvocationHandler() {
      public Object invoke(Object object, Method method, Object[] params) throws Throwable {
        final Iterator<ProcessListener> iterator;
        synchronized (myListeners) {
          iterator = myListeners.iterator();
        }
        while (iterator.hasNext()) {
          final ProcessListener processListener = iterator.next();
          try {
            method.invoke(processListener, params);
          }
          catch (Throwable e) {
            LOG.error(e);
          }
        }
        return null;
      }
    });
  }
}
