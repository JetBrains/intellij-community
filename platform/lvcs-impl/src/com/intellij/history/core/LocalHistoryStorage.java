/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.history.core;

import com.intellij.openapi.util.Clock;
import com.intellij.util.io.PagePool;
import com.intellij.util.io.storage.AbstractRecordsTable;
import com.intellij.util.io.storage.AbstractStorage;

import java.io.File;
import java.io.IOException;

public class LocalHistoryStorage extends AbstractStorage {
  public LocalHistoryStorage(String storageFilePath) throws IOException {
    super(storageFilePath);
  }

  public LocalHistoryStorage(String storageFilePath, PagePool pool) throws IOException {
    super(storageFilePath, pool);
  }

  @Override
  protected AbstractRecordsTable createRecordsTable(PagePool pool, File recordsFile) throws IOException {
    return new LocalHistoryRecordsTable(recordsFile, pool);
  }

  public long getFSTimestamp() {
    synchronized (myLock) {
      return ((LocalHistoryRecordsTable)myRecordsTable).getFSTimestamp();
    }
  }

  public void setFSTimestamp(long timestamp) {
    synchronized (myLock) {
      ((LocalHistoryRecordsTable)myRecordsTable).setFSTimestamp(timestamp);
    }
  }

  public long nextId() {
    synchronized (myLock) {
      return ((LocalHistoryRecordsTable)myRecordsTable).nextId();
    }
  }

  public int getFirstRecord() {
    synchronized (myLock) {
      return ((LocalHistoryRecordsTable)myRecordsTable).getFirstRecord();
    }
  }

  public int getLastRecord() {
    synchronized (myLock) {
      return ((LocalHistoryRecordsTable)myRecordsTable).getLastRecord();
    }
  }

  public int getPrevRecord(int record) {
    synchronized (myLock) {
      return ((LocalHistoryRecordsTable)myRecordsTable).getPrevRecord(record);
    }
  }

  public int getNextRecord(int record) {
    synchronized (myLock) {
      return ((LocalHistoryRecordsTable)myRecordsTable).getNextRecord(record);
    }
  }

  public long getTimestamp(int record) {
    synchronized (myLock) {
      return ((LocalHistoryRecordsTable)myRecordsTable).getTimestamp(record);
    }
  }

  public int createNextRecord() throws IOException {
    synchronized (myLock) {
      LocalHistoryRecordsTable table = (LocalHistoryRecordsTable)myRecordsTable;
      int id = table.createNewRecord();
      int prev = table.getLastRecord();

      table.setPrevRecord(id, prev);
      if (prev > 0) {
        table.setNextRecord(prev, id);
      }
      else {
        table.setFirstRecord(id);
      }
      table.setLastRecord(id);

      table.setTimestamp(id, Clock.getTime());

      return id;
    }
  }

  public void deleteRecord(int id) throws IOException {
    synchronized (myLock) {
      LocalHistoryRecordsTable table = (LocalHistoryRecordsTable)myRecordsTable;

      int prev = table.getPrevRecord(id);
      int next = table.getNextRecord(id);

      if (prev == 0) {
        table.setFirstRecord(next);
      }
      else {
        table.setNextRecord(prev, next);
      }

      if (next == 0) {
        table.setLastRecord(prev);
      }
      else {
        table.setPrevRecord(next, prev);
      }

      doDeleteRecord(id);
    }
  }
}
