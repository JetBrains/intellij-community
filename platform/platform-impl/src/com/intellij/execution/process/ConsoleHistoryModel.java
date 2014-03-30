package com.intellij.execution.process;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class ConsoleHistoryModel implements ModificationTracker {

  private int myHistoryCursor = -1;
  private final LinkedList<String> myHistory = new LinkedList<String>();
  private volatile long myModificationTracker;


  public void addToHistory(String statement) {
    if (StringUtil.isEmptyOrSpaces(statement)) return;

    int maxHistorySize = getMaxHistorySize();
    synchronized (myHistory) {
      myModificationTracker++;
      myHistoryCursor = -1;

      myHistory.remove(statement);
      int size = myHistory.size();
      if (size >= maxHistorySize && size > 0) {
        myHistory.removeLast();
      }
      myHistory.addFirst(statement);
    }
  }

  public int getMaxHistorySize() {
    return UISettings.getInstance().CONSOLE_COMMAND_HISTORY_LIMIT;
  }

  public void removeFromHistory(final String statement) {
    synchronized (myHistory) {
      myModificationTracker++;
      myHistoryCursor = -1;

      myHistory.remove(statement);
    }
  }

  public List<String> getHistory() {
    synchronized (myHistory) {
      return new ArrayList<String>(myHistory);
    }
  }

  public int getHistorySize() {
    synchronized (myHistory) {
      return myHistory.size();
    }
  }

  @Nullable
  public String getHistoryNext() {
    synchronized (myHistory) {
      if (myHistoryCursor < myHistory.size() - 1) {
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
      return next ? myHistoryCursor <= myHistory.size() - 1 : myHistoryCursor >= 0;
    }
  }

  public int getHistoryCursor() {
    synchronized (myHistory) {
      return myHistoryCursor;
    }
  }

  @Override
  public long getModificationCount() {
    return myModificationTracker;
  }
}
