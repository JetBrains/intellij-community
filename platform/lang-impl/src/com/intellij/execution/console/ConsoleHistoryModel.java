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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
class ConsoleHistoryModel extends SimpleModificationTracker {
  /** @noinspection FieldCanBeLocal*/
  private final ConsoleHistoryModel myMasterModel; // hard ref
  private int myIndex;
  private final LinkedList<String> myEntries;

  ConsoleHistoryModel(ConsoleHistoryModel masterModel) {
    myMasterModel = masterModel;
    myEntries = myMasterModel == null ? new LinkedList<String>() : myMasterModel.myEntries;
    resetIndex();
  }

  ConsoleHistoryModel copy() {
    return new ConsoleHistoryModel(this);
  }

  public synchronized void resetEntries(@NotNull List<String> entries) {
    myEntries.clear();
    myEntries.addAll(entries.subList(0, Math.min(entries.size(), getMaxHistorySize())));
    incModificationCount();
  }

  public synchronized void addToHistory(@Nullable String statement) {
    if (StringUtil.isEmptyOrSpaces(statement)) return;

    int maxHistorySize = getMaxHistorySize();
    myEntries.remove(statement);
    int size = myEntries.size();
    if (size >= maxHistorySize && size > 0) {
      myEntries.removeFirst();
    }
    myEntries.addLast(statement);
    incModificationCount();
  }

  @Override
  public void incModificationCount() {
    resetIndex();
    super.incModificationCount();
  }

  protected synchronized void resetIndex() {
    myIndex = myEntries.size();
  }

  public int getMaxHistorySize() {
    return UISettings.getInstance().CONSOLE_COMMAND_HISTORY_LIMIT;
  }

  public synchronized void removeFromHistory(String statement) {
    myEntries.remove(statement);
    incModificationCount();
  }

  public synchronized List<String> getEntries() {
    return new ArrayList<String>(myEntries);
  }

  public synchronized int getHistorySize() {
    return myEntries.size();
  }

  @Nullable
  public synchronized String getHistoryNext() {
    if (myIndex >= 0) --myIndex;
    return getCurrentEntry();
  }

  @Nullable
  public synchronized String getHistoryPrev() {
    if (myIndex <= myEntries.size() - 1) ++myIndex;
    return getCurrentEntry();
  }

  public synchronized boolean hasHistory(final boolean next) {
    return next ? myIndex > 0 : myIndex < myEntries.size() - 1;
  }

  synchronized String getCurrentEntry() {
    return myIndex >= 0 && myIndex < myEntries.size() ? myEntries.get(myIndex) : null;
  }

  synchronized int getCurrentIndex() {
    return myIndex;
  }
}
