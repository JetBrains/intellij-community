package com.intellij.debugger.engine;

import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.engine.managerThread.DebuggerManagerThread;
import com.intellij.debugger.engine.managerThread.DebuggerCommand;
import com.intellij.debugger.engine.managerThread.SuspendContextCommand;
import com.intellij.debugger.impl.EventQueue;
import com.intellij.debugger.impl.InvokeAndWaitThread;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorListenerAdapter;
import com.intellij.openapi.progress.util.ProgressWindowWithNotification;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.Alarm;
import com.sun.jdi.VMDisconnectedException;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Feb 25, 2004
 * Time: 4:51:20 PM
 * To change this template use File | Settings | File Templates.
 */
public class DebuggerManagerThreadImpl extends InvokeAndWaitThread<DebuggerCommandImpl> implements DebuggerManagerThread {
  public static final int HIGH_PRIORITY = 0;
  public static final int NORMAL_PRIORITY = 1;

  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.DebuggerManagerThreadImpl");
  public static final int COMMAND_TIMEOUT = 3000;

  DebuggerManagerThreadImpl() {
    //noinspection HardCodedStringLiteral
    super("DebuggerManagerThreadImpl", 2);
  }

  public static DebuggerManagerThreadImpl createTestInstance() {
    return new DebuggerManagerThreadImpl();
  }

  public static boolean isManagerThread() {
    return currentThread() instanceof DebuggerManagerThreadImpl;
  }

  public static void assertIsManagerThread() {
    LOG.assertTrue(isManagerThread(), "Should be invoked in manager thread, use DebuggerManagerThreadImpl.getInstance(..).invoke...");
  }

  public void invokeAndWait(DebuggerCommandImpl managerCommand, int priority) {
    LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread());
    LOG.assertTrue(!(currentThread() instanceof DebuggerManagerThreadImpl),
                   "Should be invoked outside manager thread, use DebuggerManagerThreadImpl.getInstance(..).invoke...");
    super.invokeAndWait(managerCommand, priority);
  }

  public void invokeAndWait(DebuggerCommandImpl managerCommand) {
    LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread());
    LOG.assertTrue(!(currentThread() instanceof DebuggerManagerThreadImpl),
                   "Should be invoked outside manager thread, use DebuggerManagerThreadImpl.getInstance(..).invoke...");
    super.invokeAndWait(managerCommand, NORMAL_PRIORITY);    
  }

  public void invoke(DebuggerCommandImpl managerCommand) {
    invoke(managerCommand, NORMAL_PRIORITY);
  }

  public void invoke(DebuggerCommandImpl managerCommand, int priority) {
    if (currentThread() instanceof DebuggerManagerThreadImpl) {
      processEvent(managerCommand);
    }
    else {
      invokeLater(managerCommand, priority);
    }
  }

  public void invokeLater(DebuggerCommandImpl managerCommand, int priority) {
    if(myEvents.isClosed()) {
      managerCommand.notifyCancelled();
    }
    else {
      super.invokeLater(managerCommand, priority);
    }
  }

  public void  invokeLater(DebuggerCommandImpl managerCommand) {
    invokeLater(managerCommand, NORMAL_PRIORITY);
  }

  /**
   * waits COMMAND_TIMEOUT milliseconds
   * if worker thread is still processing the same command
   * calls terminateCommand
   */

  public void terminateAndInvoke(DebuggerCommandImpl command, int terminateTimeout) {
    final DebuggerCommandImpl[] currentCommand = new DebuggerCommandImpl[1];

    myEvents.getCurrentEvent(new EventQueue.EventGetter<DebuggerCommandImpl>() {
      public void event(DebuggerCommandImpl command) {
        currentCommand[0] = command;
      }
    });

    invoke(command, HIGH_PRIORITY);
    final Alarm alarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);
    alarm.addRequest(new Runnable() {
      public void run() {
        if (currentCommand[0] != null) {
          terminateCommand(currentCommand[0]);
        }
      }
    }, terminateTimeout);
  }

  /**
   * interrupts command in old worker thread and closes old thread.
   * then starts new worker thread
   * <p/>
   * Note: if old thread is working and InterruptedException is not thrown
   * command will continue execution in old thread simulteniously with
   * commands in new thread
   * <p/>
   * use very carefully!
   */

  public void terminateCommand(final DebuggerCommandImpl command) {
    myEvents.getCurrentEvent(new EventQueue.EventGetter<DebuggerCommandImpl>() {
      public void event(DebuggerCommandImpl currentCommand) {
        if (command == currentCommand) {
          restartWorkerThread();
        }
      }
    });
  }

  public DebuggerCommandImpl getCurrentCommand() {
    final DebuggerCommandImpl[] command = new DebuggerCommandImpl[1];

    myEvents.getCurrentEvent(new EventQueue.EventGetter<DebuggerCommandImpl>() {
      public void event(DebuggerCommandImpl debuggerCommand) {
        command[0] = debuggerCommand;
      }
    });

    return command[0];
  }


  public void processEvent(DebuggerCommandImpl managerCommand) {
    assertIsManagerThread();
    LOG.assertTrue(managerCommand != null);
    try {
      if(myEvents.isClosed()) {
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
      public void cancelled() {
        command.release();
      }
    });

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        ProgressManager.getInstance().runProcess(new Runnable() {
          public void run() {
            invokeAndWait(command, HIGH_PRIORITY);
          }
        }, progressWindow);
      }
    });
  }


  public void startLongProcessAndFork(Runnable process) {
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
        protected void action() throws Exception {
          switchToRequest(request);
        }

        protected void commandCancelled() {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Event queue was closed, killing request");
          }
          request.interrupt();
        }
      }, NORMAL_PRIORITY);
    }
  }

  public void invokeCommand(final DebuggerCommand command) {
    if(command instanceof SuspendContextCommand) {
      SuspendContextCommand suspendContextCommand = ((SuspendContextCommand) command);
      invokeLater(new SuspendContextCommandImpl((SuspendContextImpl)suspendContextCommand.getSuspendContext()) {
        public void contextAction() throws Exception {
          command.action();
        }

        protected void commandCancelled() {
          command.commandCancelled();
        }
      });
    }
    else {
      invokeLater(new DebuggerCommandImpl() {
        protected void action() throws Exception {
          command.action();
        }

        protected void commandCancelled() {
          command.commandCancelled();
        }
      });
    }

  }
}
