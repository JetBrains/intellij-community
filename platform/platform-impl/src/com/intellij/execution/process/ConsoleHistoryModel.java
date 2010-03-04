package com.intellij.execution.process;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.EmptyAction;
import com.intellij.util.PairProcessor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class ConsoleHistoryModel {

  public static final int DEFAULT_MAX_SIZE = 20;

  private int myHistoryCursor;
  private int myMaxHistorySize = DEFAULT_MAX_SIZE;
  private final LinkedList<String> myHistory = new LinkedList<String>();


  public void addToHistory(final String statement) {
    synchronized (myHistory) {
      myHistoryCursor = -1;
      myHistory.remove(statement);
      if (myHistory.size() >= myMaxHistorySize) {
        myHistory.removeLast();
      }
      myHistory.addFirst(statement);
    }
  }

  public List<String> getHistory() {
    synchronized (myHistory) {
      return new ArrayList<String>(myHistory);
    }
  }

  public int getMaxHistorySize() {
    synchronized (myHistory) {
      return myMaxHistorySize;
    }
  }

  public void setMaxHistorySize(final int maxHistorySize) {
    synchronized (myHistory) {
      myMaxHistorySize = maxHistorySize;
    }
  }

  @Nullable
  public String getHistoryNext() {
    synchronized (myHistory) {
      if (myHistoryCursor < myHistory.size()-1) {
        return myHistory.get(++myHistoryCursor);
      }
      else {
        if (myHistoryCursor == myHistory.size() - 1) myHistoryCursor++;
        return null;
      }
    }
  }

  @Nullable
  public String getHistoryPrev() {
    synchronized (myHistory) {
      if (myHistoryCursor > 0) {
        return myHistory.get(--myHistoryCursor);
      }
      else {
        if (myHistoryCursor == 0) myHistoryCursor--;
        return null;
      }
    }
  }

  public boolean hasHistory(final boolean next) {
    synchronized (myHistory) {
      return next? myHistoryCursor <= myHistory.size() - 1 : myHistoryCursor >= 0;
    }
  }

  public static AnAction createHistoryAction(final ConsoleHistoryModel model, final boolean next, final PairProcessor<AnActionEvent,String> processor) {
    final AnAction action = new AnAction(null, null, null) {
      @Override
      public void actionPerformed(final AnActionEvent e) {
        processor.process(e, next ? model.getHistoryNext() : model.getHistoryPrev());
      }

      @Override
      public void update(final AnActionEvent e) {
        e.getPresentation().setEnabled(model.hasHistory(next));
      }
    };
    EmptyAction.setupAction(action, next? "Console.History.Next" : "Console.History.Previous", null);
    return action;
  }

}
