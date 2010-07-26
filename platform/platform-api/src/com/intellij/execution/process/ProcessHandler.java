/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ProcessHandler extends UserDataHolderBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.ProcessHandler");
  public static final Key<Boolean> SILENTLY_DESTROY_ON_CLOSE = Key.create("SILENTLY_DESTROY_ON_CLOSE");

  private final List<ProcessListener> myListeners = ContainerUtil.createEmptyCOWList();

  private static final int STATE_INITIAL     = 0;
  private static final int STATE_RUNNING     = 1;
  private static final int STATE_TERMINATING = 2;
  private static final int STATE_TERMINATED  = 3;

  private final AtomicInteger myState = new AtomicInteger(STATE_INITIAL);

  private final Semaphore    myWaitSemaphore;
  private final ProcessListener myEventMulticaster;

  protected ProcessHandler() {
    myEventMulticaster = createEventMulticaster();
    myWaitSemaphore = new Semaphore();
    myWaitSemaphore.down();
  }

  public synchronized void startNotify() {
    if(myState.compareAndSet(STATE_INITIAL, STATE_RUNNING)) {
      myEventMulticaster.startNotified(new ProcessEvent(this));
    }
    else {
      LOG.error("startNotify called already");
    }
  }

  protected abstract void destroyProcessImpl();

  protected abstract void detachProcessImpl();

  public abstract boolean detachIsDefault();

  public void waitFor() {
    try {
      myWaitSemaphore.waitFor();
    }
    catch (ProcessCanceledException e) {
      // Ignore
    }
  }

  public boolean waitFor(long timeoutInMilliseconds) {
    try {
      return myWaitSemaphore.waitFor(timeoutInMilliseconds);
    }
    catch (ProcessCanceledException e) {
      return false;
    }
  }

  public void destroyProcess() {
    afterStartNotified(new Runnable() {
      public void run() {
        if (myState.compareAndSet(STATE_RUNNING, STATE_TERMINATING)) {
          fireProcessWillTerminate(true);
          destroyProcessImpl();
        }
      }
    });
  }

  public void detachProcess() {
    afterStartNotified(new Runnable() {
      public void run() {
        if (myState.compareAndSet(STATE_RUNNING, STATE_TERMINATING)) {
          fireProcessWillTerminate(false);
          detachProcessImpl();
        }
      }
    });
  }

  public boolean isProcessTerminated() {
    return myState.get() == STATE_TERMINATED;
  }

  public boolean isProcessTerminating() {
    return myState.get() == STATE_TERMINATING;
  }

  public void addProcessListener(final ProcessListener listener) {
    myListeners.add(listener);
  }

  public void removeProcessListener(final ProcessListener listener) {
    myListeners.remove(listener);
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

        if (myState.compareAndSet(STATE_RUNNING, STATE_TERMINATING)) {
          try {
            fireProcessWillTerminate(willBeDestroyed);
          }
          catch (Throwable e) {
            LOG.error(e);
          }
        }

        if (myState.compareAndSet(STATE_TERMINATING, STATE_TERMINATED)) {
          try {
            myEventMulticaster.processTerminated(new ProcessEvent(ProcessHandler.this, exitCode));
          }
          catch (Throwable e) {
            LOG.error(e);
          }
          finally {
            myWaitSemaphore.up();
          }
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

  private synchronized void afterStartNotified(final Runnable runnable) {
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
    return myState.get() > STATE_INITIAL;
  }

  private ProcessListener createEventMulticaster() {
    final Class<ProcessListener> listenerClass = ProcessListener.class;
    return (ProcessListener)Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class[] {listenerClass}, new InvocationHandler() {
      public Object invoke(Object object, Method method, Object[] params) throws Throwable {
        for (ProcessListener listener : myListeners) {
          try {
            method.invoke(listener, params);
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
