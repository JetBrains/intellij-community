package com.intellij.util.concurrency.readwrite;

import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;

public class CommandWaiter extends AbstractWaiter implements CommandListener {

  private Runnable myCommandRunnable;

  public CommandWaiter(Runnable aCommandRunnable) {
    myCommandRunnable = aCommandRunnable;

    setFinished(false);
    CommandProcessor.getInstance().addCommandListener(this);
  }

  public void beforeCommandFinished(CommandEvent event) {
  }

  public void commandFinished(CommandEvent event) {
    if (event.getCommand() == myCommandRunnable) {
      CommandProcessor.getInstance().removeCommandListener(this);
      setFinished(true);
    }
  }

  public void commandStarted(CommandEvent event) {
  }

  public void undoTransparentActionStarted() {
  }

  public void undoTransparentActionFinished() {
  }
}
