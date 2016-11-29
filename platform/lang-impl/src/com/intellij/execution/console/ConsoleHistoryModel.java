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
package com.intellij.execution.console;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
class ConsoleHistoryModel extends SimpleModificationTracker {
  /** @noinspection FieldCanBeLocal*/
  private final Object myLock;
  private final LinkedList<String> myEntries;
  private int myIndex;

  ConsoleHistoryModel(@Nullable ConsoleHistoryModel masterModel) {
    myEntries = masterModel == null ? new LinkedList<>() : masterModel.myEntries;
    myLock = masterModel == null ? this : masterModel.myLock;  // hard ref to master model
    resetIndex();
  }

  ConsoleHistoryModel copy() {
    return new ConsoleHistoryModel(this);
  }

  public void resetEntries(@NotNull List<String> entries) {
    synchronized (myLock) {
      myEntries.clear();
      myEntries.addAll(entries.subList(0, Math.min(entries.size(), getMaxHistorySize())));
      incModificationCount();
    }
  }

  public void addToHistory(@Nullable String statement) {
    if (StringUtil.isEmptyOrSpaces(statement)) return;

    synchronized (myLock) {
      int maxHistorySize = getMaxHistorySize();
      myEntries.remove(statement);
      int size = myEntries.size();
      if (size >= maxHistorySize && size > 0) {
        myEntries.removeFirst();
      }
      myEntries.addLast(statement);
      incModificationCount();
    }
  }

  @Override
  public void incModificationCount() {
    resetIndex();
    super.incModificationCount();
  }

  protected void resetIndex() {
    synchronized (myLock) {
      myIndex = myEntries.size();
    }
  }

  public int getMaxHistorySize() {
    return UISettings.getInstance().CONSOLE_COMMAND_HISTORY_LIMIT;
  }

  public void removeFromHistory(String statement) {
    synchronized (myLock) {
      myEntries.remove(statement);
      incModificationCount();
    }
  }

  public List<String> getEntries() {
    synchronized (myLock) {
      return ContainerUtil.newArrayList(myEntries);
    }
  }

  public boolean isEmpty() {
    synchronized (myLock) {
      return myEntries.isEmpty();
    }
  }

  public int getHistorySize() {
    synchronized (myLock) {
      return myEntries.size();
    }
  }

  @Nullable
  public String getHistoryNext() {
    synchronized (myLock) {
      if (myIndex >= 0) --myIndex;
      return getCurrentEntry();
    }
  }

  @Nullable
  public String getHistoryPrev() {
    synchronized (myLock) {
      if (myIndex <= myEntries.size() - 1) ++myIndex;
      return getCurrentEntry();
    }
  }

  public boolean hasHistory(final boolean next) {
    synchronized (myLock) {
      return next ? myIndex > 0 : myIndex < myEntries.size() - 1;
    }
  }

  String getCurrentEntry() {
    synchronized (myLock) {
      return myIndex >= 0 && myIndex < myEntries.size() ? myEntries.get(myIndex) : null;
    }
  }

  int getCurrentIndex() {
    synchronized (myLock) {
      return myIndex;
    }
  }
}
