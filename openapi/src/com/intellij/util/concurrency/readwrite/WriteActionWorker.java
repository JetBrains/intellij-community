package com.intellij.util.concurrency.readwrite;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;


public abstract class WriteActionWorker extends ActiveRunnableWrapper {

  private void start() {
    WriteActionWaiter waiter = new WriteActionWaiter(this);

    if (EventQueue.isDispatchThread()) {
      ApplicationManager.getApplication().runWriteAction(WriteActionWorker.this);
    } else {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(WriteActionWorker.this);
        }
      });
    }

    waiter.waitForCompletion();
  }

  public static Object run(final ActiveRunnable aActiveRunnable) throws Throwable {
    WriteActionWorker worker = new WriteActionWorker() {
      public Object doRun() throws Throwable {
        return aActiveRunnable.run();
      }
    };

    worker.start();
    worker.throwException();

    return worker.getResult();
  }

  private static class CommandWrapper extends ActiveRunnableWrapper {

    private ActiveRunnable myWriteActionRunnable;

    public CommandWrapper(ActiveRunnable aWriteActionRunnable) {
      myWriteActionRunnable = aWriteActionRunnable;
    }

    public Object doRun() throws Throwable {
      return WriteActionWorker.run(myWriteActionRunnable);
    }
  }

  public static Object runInCommand(final Project project, final ActiveRunnable aActiveRunnable, final String aCommandName) throws Throwable {
    final CommandWrapper commandWrapper = new CommandWrapper(aActiveRunnable);

    CommandWaiter commandWaiter = new CommandWaiter(commandWrapper);

    if (EventQueue.isDispatchThread()) {
      CommandProcessor.getInstance().executeCommand(project, commandWrapper, aCommandName, null);
    } else {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          CommandProcessor.getInstance().executeCommand(project, commandWrapper, aCommandName, null);
        }
      });
    }

    commandWaiter.waitForCompletion();
    commandWrapper.throwException();

    return commandWrapper.getResult();
  }
}
