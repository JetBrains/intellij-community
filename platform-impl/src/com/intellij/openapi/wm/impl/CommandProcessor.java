package com.intellij.openapi.wm.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.impl.commands.FinalizableCommand;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CommandProcessor implements Runnable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.CommandProcessor");
  private final Object myLock;

  private final List<Bulk> myCommandList = new ArrayList<Bulk>();
  private final Map<Bulk, Condition> myCommandToExpire = new HashMap<Bulk, Condition>();
  private int myCommandCount;

  public CommandProcessor() {
    myLock = new Object();
  }

  public final int getCommandCount() {
    synchronized (myLock) {
      return myCommandCount;
    }
  }

  /**
   * Executes passed batch of commands. Note, that the processor surround the
   * commands with BlockFocusEventsCmd - UnbockFocusEventsCmd. It's required to
   * prevent focus handling of events which is caused by the commands to be executed.
   */
  public final void execute(final java.util.List commandList, Condition expired) {
    synchronized (myLock) {
      final boolean isBusy = myCommandCount > 0;

      final Bulk bulk = new Bulk(commandList);
      myCommandList.add(bulk);
      myCommandToExpire.put(bulk, expired);
      myCommandCount += commandList.size();

      if (!isBusy) {
        run();
      }
    }
  }

  public final void run() {
    synchronized (myLock) {
      final Bulk bulk = getNextCommandBulk();
      if (bulk == null) return;

      final Condition bulkExpire = myCommandToExpire.get(bulk);

      if (bulk.myList.size() > 0) {
        final FinalizableCommand command = (FinalizableCommand)bulk.myList.remove(0);
        myCommandCount--;

        final Condition expire = command.getExpired() != null ? command.getExpired() : bulkExpire;

        if (LOG.isDebugEnabled()) {
          LOG.debug("CommandProcessor.run " + command);
        }
        // max. I'm not actually quite sure this should have NON_MODAL modality but it should
        // definitely have some since runnables in command list may (and do) request some PSI activity

        final boolean queueNext = myCommandCount > 0;
        ApplicationManager.getApplication().getInvokator().invokeLater(command, ModalityState.NON_MODAL, expire == null ? Condition.FALSE : expire).doWhenDone(new Runnable() {
          public void run() {
            if (queueNext) {
              CommandProcessor.this.run();
            }
          }
        });
      }
    }
  }

  @Nullable
  private Bulk getNextCommandBulk() {
    while (myCommandList.size() > 0) {
      final Bulk candidate = myCommandList.get(0);
      if (candidate.myList.size() > 0) {
        return candidate;
      } else {
        myCommandList.remove(0);
        if (!myCommandList.contains(candidate)) {
          myCommandToExpire.remove(candidate);
        }
      }
    }

    return null;
  }

  private class Bulk {
    List myList;

    private Bulk(final List list) {
      myList = list;
    }
  }
}
