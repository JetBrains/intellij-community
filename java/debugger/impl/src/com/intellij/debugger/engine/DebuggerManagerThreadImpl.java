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
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.managerThread.DebuggerCommand;
import com.intellij.debugger.engine.managerThread.DebuggerManagerThread;
import com.intellij.debugger.engine.managerThread.SuspendContextCommand;
import com.intellij.debugger.impl.InvokeAndWaitThread;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorListenerAdapter;
import com.intellij.openapi.progress.util.ProgressWindowWithNotification;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import com.sun.jdi.VMDisconnectedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * @author lex
 */
public class DebuggerManagerThreadImpl extends InvokeAndWaitThread<DebuggerCommandImpl> implements DebuggerManagerThread, Disposable {
  private static final Logger LOG = Logger.getInstance(DebuggerManagerThreadImpl.class);
  public static final int COMMAND_TIMEOUT = 3000;

  private volatile boolean myDisposed;

  DebuggerManagerThreadImpl(@NotNull Disposable parent, Project project) {
    super(project);
    Disposer.register(parent, this);
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }

  @TestOnly
  public static DebuggerManagerThreadImpl createTestInstance(@NotNull Disposable parent, Project project) {
    return new DebuggerManagerThreadImpl(parent, project);
  }

  public static boolean isManagerThread() {
    return currentThread() instanceof DebuggerManagerThreadImpl;
  }

  public static void assertIsManagerThread() {
    LOG.assertTrue(isManagerThread(), "Should be invoked in manager thread, use DebuggerManagerThreadImpl.getInstance(..).invoke...");
  }

  @Override
  public void invokeAndWait(DebuggerCommandImpl managerCommand) {
    LOG.assertTrue(!isManagerThread(), "Should be invoked outside manager thread, use DebuggerManagerThreadImpl.getInstance(..).invoke...");
    super.invokeAndWait(managerCommand);
  }

  public void invoke(DebuggerCommandImpl managerCommand) {
    if (currentThread() == this) {
      processEvent(managerCommand);
    }
    else {
      schedule(managerCommand);
    }
  }

  @Override
  public boolean pushBack(DebuggerCommandImpl managerCommand) {
    final boolean pushed = super.pushBack(managerCommand);
    if (!pushed) {
      managerCommand.notifyCancelled();
    }
    return pushed;
  }

  @Override
  public boolean schedule(DebuggerCommandImpl managerCommand) {
    final boolean scheduled = super.schedule(managerCommand);
    if (!scheduled) {
      managerCommand.notifyCancelled();
    }
    return scheduled;
  }

  /**
   * waits COMMAND_TIMEOUT milliseconds
   * if worker thread is still processing the same command
   * calls terminateCommand
   */
  public void terminateAndInvoke(DebuggerCommandImpl command, int terminateTimeout) {
    final DebuggerCommandImpl currentCommand = myEvents.getCurrentEvent();

    invoke(command);

    if (currentCommand != null) {
      final Alarm alarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
      alarm.addRequest(new Runnable() {
        @Override
        public void run() {
          try {
            if (currentCommand == myEvents.getCurrentEvent()) {
              // if current command is still in progress, cancel it
              getCurrentRequest().requestStop();
              try {
                getCurrentRequest().join();
              }
              catch (InterruptedException ignored) {
              }
              catch (Exception e) {
                throw new RuntimeException(e);
              }
              finally {
                if (!myDisposed) {
                  startNewWorkerThread();
                }
              }
            }
          }
          finally {
            Disposer.dispose(alarm);
          }
        }
      }, terminateTimeout);
    }
  }


  @Override
  public void processEvent(@NotNull DebuggerCommandImpl managerCommand) {
    assertIsManagerThread();
    try {
      if (myEvents.isClosed()) {
        managerCommand.notifyCancelled();
      }
      else {
        managerCommand.run();
      }
    }
    catch (VMDisconnectedException e) {
      LOG.debug(e);
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  public void startProgress(final DebuggerCommandImpl command, final ProgressWindowWithNotification progressWindow) {
    progressWindow.addListener(new ProgressIndicatorListenerAdapter() {
      @Override
      public void cancelled() {
        command.release();
      }
    });

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        ProgressManager.getInstance().runProcess(new Runnable() {
          @Override
          public void run() {
            invokeAndWait(command);
          }
        }, progressWindow);
      }
    });
  }


  public void startLongProcessAndFork(Runnable process) {
    assertIsManagerThread();
    startNewWorkerThread();

    try {
      process.run();
    }
    finally {
      final WorkerThreadRequest request = getCurrentThreadRequest();

      if (LOG.isDebugEnabled()) {
        LOG.debug("Switching back to " + request);
      }

      super.invokeAndWait(new DebuggerCommandImpl() {
        @Override
        protected void action() throws Exception {
          switchToRequest(request);
        }

        @Override
        protected void commandCancelled() {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Event queue was closed, killing request");
          }
          request.requestStop();
        }
      });
    }
  }

  @Override
  public void invokeCommand(final DebuggerCommand command) {
    if(command instanceof SuspendContextCommand) {
      SuspendContextCommand suspendContextCommand = (SuspendContextCommand)command;
      schedule(new SuspendContextCommandImpl((SuspendContextImpl)suspendContextCommand.getSuspendContext()) {
          @Override
          public void contextAction() throws Exception {
            command.action();
          }

          @Override
          protected void commandCancelled() {
            command.commandCancelled();
          }
        });
    }
    else {
      schedule(new DebuggerCommandImpl() {
        @Override
        protected void action() throws Exception {
          command.action();
        }

        @Override
        protected void commandCancelled() {
          command.commandCancelled();
        }
      });
    }

  }

  public void restartIfNeeded () {
    if (myEvents.isClosed()) {
      myEvents.reopen();
      startNewWorkerThread();
    }
  }
}
