/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class ConsoleHistoryModel extends SimpleModificationTracker {
  private int myHistoryCursor = -1;
  private final LinkedList<String> myHistory = new LinkedList<String>();

  public void addToHistory(String statement) {
    if (StringUtil.isEmptyOrSpaces(statement)) return;

    int maxHistorySize = getMaxHistorySize();
    synchronized (myHistory) {
      incModificationCount();
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
      incModificationCount();
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
}
