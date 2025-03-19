// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core;

import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Pair;
import com.intellij.util.io.StorageLockContext;
import com.intellij.util.io.storage.AbstractRecordsTable;
import com.intellij.util.io.storage.AbstractStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

@ApiStatus.Internal
public final class LocalHistoryStorage extends AbstractStorage {
  private static final StorageLockContext STORAGE_LOCK_CONTEXT = new StorageLockContext();

  public LocalHistoryStorage(@NotNull Path storageFilePath) throws IOException {
    super(storageFilePath, STORAGE_LOCK_CONTEXT);
  }

  @Override
  protected AbstractRecordsTable createRecordsTable(@NotNull StorageLockContext pool, @NotNull Path recordsFile) throws IOException {
    return new LocalHistoryRecordsTable(recordsFile, pool);
  }

  public long getFSTimestamp() throws IOException {
    return withReadLock(() -> {
      return ((LocalHistoryRecordsTable)myRecordsTable).getFSTimestamp();
    });
  }

  public void setFSTimestamp(long timestamp) throws IOException {
    withWriteLock(() -> {
      ((LocalHistoryRecordsTable)myRecordsTable).setFSTimestamp(timestamp);
    });
  }

  public long getLastId() throws IOException {
    return withReadLock(() -> {
      return ((LocalHistoryRecordsTable)myRecordsTable).getLastId();
    });
  }

  public void setLastId(long lastId) throws IOException {
    withWriteLock(() -> {
      ((LocalHistoryRecordsTable)myRecordsTable).setLastId(lastId);
    });
  }

  public int getFirstRecord() throws IOException {
    return withReadLock(() -> {
      return ((LocalHistoryRecordsTable)myRecordsTable).getFirstRecord();
    });
  }

  public int getLastRecord() throws IOException {
    return withReadLock(() -> {
      return ((LocalHistoryRecordsTable)myRecordsTable).getLastRecord();
    });
  }

  public int getPrevRecord(int record) throws IOException {
    return withReadLock(() -> {
      return ((LocalHistoryRecordsTable)myRecordsTable).getPrevRecord(record);
    });
  }

  public int getNextRecord(int record) throws IOException {
    return withReadLock(() -> {
      return ((LocalHistoryRecordsTable)myRecordsTable).getNextRecord(record);
    });
  }

  public long getTimestamp(int record) throws IOException {
    return withReadLock(() -> {
      return ((LocalHistoryRecordsTable)myRecordsTable).getTimestamp(record);
    });
  }

  public Pair<Long, Integer> getOffsetAndSize(int id) throws IOException {
    return withReadLock(() -> {
      return Pair.create(myRecordsTable.getAddress(id), myRecordsTable.getSize(id));
    });
  }

  public int createNextRecord() throws IOException {
    return withWriteLock(() -> {
      LocalHistoryRecordsTable table = (LocalHistoryRecordsTable)myRecordsTable;
      int id = table.createNewRecord();
      int prev = table.getLastRecord();

      if (prev > 0) {
        table.setPrevRecord(id, prev);
        table.setNextRecord(prev, id);
      }
      else {
        table.setFirstRecord(id);
      }
      table.setLastRecord(id);

      table.setTimestamp(id, Clock.getTime());

      return id;
    });
  }

  public void deleteRecordsUpTo(int idInclusively) throws IOException {
    withWriteLock(() -> {
      LocalHistoryRecordsTable table = (LocalHistoryRecordsTable)myRecordsTable;

      int each = table.getFirstRecord();
      if (each == 0) return;

      while (each != 0) {
        boolean stop = each == idInclusively;

        int next = table.getNextRecord(each);
        doDeleteRecord(each);
        each = next;

        if (stop) break;
      }

      if (each == 0) {
        table.setFirstRecord(0);
        table.setLastRecord(0);
      }
      else {
        table.setFirstRecord(each);
        table.setPrevRecord(each, 0);
      }
    });
  }
}
