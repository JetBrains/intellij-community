// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.managerThread.DebuggerCommand;
import com.intellij.debugger.engine.managerThread.DebuggerManagerThread;
import com.intellij.debugger.engine.managerThread.SuspendContextCommand;
import com.intellij.debugger.impl.InvokeAndWaitThread;
import com.intellij.debugger.impl.PrioritizedTask;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorListener;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.sun.jdi.VMDisconnectedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

public class DebuggerManagerThreadImpl extends InvokeAndWaitThread<DebuggerCommandImpl> implements DebuggerManagerThread, Disposable {
  private static final Logger LOG = Logger.getInstance(DebuggerManagerThreadImpl.class);
  private static final ThreadLocal<LinkedList<DebuggerCommandImpl>> myCurrentCommands = ThreadLocal.withInitial(LinkedList::new);

  static final int COMMAND_TIMEOUT = 3000;

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

  public void invoke(PrioritizedTask.Priority priority, Runnable runnable) {
    invoke(new DebuggerCommandImpl(priority) {
      @Override
      protected void action() {
        runnable.run();
      }
    });
  }

  @Override
  public boolean pushBack(DebuggerCommandImpl managerCommand) {
    final boolean pushed = super.pushBack(managerCommand);
    if (!pushed) {
      managerCommand.notifyCancelled();
    }
    return pushed;
  }

  public void schedule(PrioritizedTask.Priority priority, Runnable runnable) {
    schedule(new DebuggerCommandImpl(priority) {
      @Override
      protected void action() {
        runnable.run();
      }
    });
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
  public void terminateAndInvoke(DebuggerCommandImpl command, int terminateTimeoutMillis) {
    final DebuggerCommandImpl currentCommand = myEvents.getCurrentEvent();

    invoke(command);

    if (currentCommand != null) {
      AppExecutorUtil.getAppScheduledExecutorService().schedule(
        () -> {
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
        }, terminateTimeoutMillis, TimeUnit.MILLISECONDS);
    }
  }

  @Override
  public void processEvent(@NotNull DebuggerCommandImpl managerCommand) {
    assertIsManagerThread();
    myCurrentCommands.get().push(managerCommand);
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
    finally {
      myCurrentCommands.get().pop();
    }
  }

  public static DebuggerCommandImpl getCurrentCommand() {
    return myCurrentCommands.get().peek();
  }

  public void startProgress(DebuggerCommandImpl command, ProgressWindow progressWindow) {
    new ProgressIndicatorListener() {
      @Override
      public void cancelled() {
        command.release();
      }
    }.installToProgress(progressWindow);

    ApplicationManager.getApplication().executeOnPooledThread(
      () -> ProgressManager.getInstance().runProcess(() -> invokeAndWait(command), progressWindow));
  }


  void startLongProcessAndFork(Runnable process) {
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
        protected void action() {
          switchToRequest(request);
        }

        @Override
        protected void commandCancelled() {
          LOG.debug("Event queue was closed, killing request");
          request.requestStop();
        }
      });
    }
  }

  @Override
  public void invokeCommand(final DebuggerCommand command) {
    if (command instanceof SuspendContextCommand suspendContextCommand) {
      schedule(new SuspendContextCommandImpl((SuspendContextImpl)suspendContextCommand.getSuspendContext()) {
        @Override
        public void contextAction(@NotNull SuspendContextImpl suspendContext) {
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
        protected void action() {
          command.action();
        }

        @Override
        protected void commandCancelled() {
          command.commandCancelled();
        }
      });
    }
  }

  public boolean isIdle() {
    return myEvents.isEmpty();
  }

  public boolean hasAsyncCommands() {
    return myEvents.hasAsyncCommands();
  }

  void restartIfNeeded() {
    if (myEvents.isClosed()) {
      myEvents.reopen();
      startNewWorkerThread();
    }
  }
}
