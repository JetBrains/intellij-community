/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Gregory.Shrago
 */
class DefaultConsoleHistoryModel extends SimpleModificationTracker implements ConsoleHistoryModel {
  /**
   * @noinspection FieldCanBeLocal
   */

  /**
   * @noinspection MismatchedQueryAndUpdateOfCollection
   */
  private final static FactoryMap<String, DefaultConsoleHistoryModel> ourModels = new FactoryMap<String, DefaultConsoleHistoryModel>() {
    @Override
    protected Map<String, DefaultConsoleHistoryModel> createMap() {
      return ContainerUtil.createConcurrentWeakValueMap();
    }

    @Override
    protected DefaultConsoleHistoryModel create(String key) {
      return new DefaultConsoleHistoryModel(null);
    }
  };

  public static DefaultConsoleHistoryModel createModel(String persistenceId) {
    return ourModels.get(persistenceId).copy();
  }
  private final Object myLock;
  private final LinkedList<String> myEntries;
  private int myIndex;

  DefaultConsoleHistoryModel(@Nullable DefaultConsoleHistoryModel masterModel) {
    myEntries = masterModel == null ? new LinkedList<>() : masterModel.myEntries;
    myLock = masterModel == null ? this : masterModel.myLock;  // hard ref to master model
    resetIndex();
  }

  public DefaultConsoleHistoryModel copy() {
    return new DefaultConsoleHistoryModel(this);
  }

  @Override
  public void resetEntries(@NotNull List<String> entries) {
    synchronized (myLock) {
      myEntries.clear();
      myEntries.addAll(entries.subList(0, Math.min(entries.size(), getMaxHistorySize())));
      incModificationCount();
    }
  }

  @Override
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

  @Override
  public int getMaxHistorySize() {
    return UISettings.getInstance().getConsoleCommandHistoryLimit();
  }

  @Override
  public void removeFromHistory(String statement) {
    synchronized (myLock) {
      myEntries.remove(statement);
      incModificationCount();
    }
  }

  @Override
  public List<String> getEntries() {
    synchronized (myLock) {
      return ContainerUtil.newArrayList(myEntries);
    }
  }

  @Override
  public boolean isEmpty() {
    synchronized (myLock) {
      return myEntries.isEmpty();
    }
  }

  @Override
  public int getHistorySize() {
    synchronized (myLock) {
      return myEntries.size();
    }
  }

  @Override
  @Nullable
  public String getHistoryNext() {
    synchronized (myLock) {
      if (myIndex >= 0) --myIndex;
      return getCurrentEntry();
    }
  }

  @Override
  @Nullable
  public String getHistoryPrev() {
    synchronized (myLock) {
      if (myIndex <= myEntries.size() - 1) ++myIndex;
      return getCurrentEntry();
    }
  }

  @Override
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

  public int getCurrentIndex() {
    synchronized (myLock) {
      return myIndex;
    }
  }
}
