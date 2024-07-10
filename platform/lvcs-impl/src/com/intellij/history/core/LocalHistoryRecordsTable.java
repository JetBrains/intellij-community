// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.core;

import com.intellij.util.io.StorageLockContext;
import com.intellij.util.io.storage.AbstractRecordsTable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

@ApiStatus.Internal
public final class LocalHistoryRecordsTable extends AbstractRecordsTable {
  private static final int VERSION = 4;

  private static final int LAST_ID_OFFSET = DEFAULT_HEADER_SIZE;
  private static final int FIRST_RECORD_OFFSET = LAST_ID_OFFSET + 8;
  private static final int LAST_RECORD_OFFSET = FIRST_RECORD_OFFSET + 4;
  private static final int FS_TIMESTAMP_OFFSET = LAST_RECORD_OFFSET + 4;
  private static final int HEADER_SIZE = FS_TIMESTAMP_OFFSET + 8;

  private static final int PREV_RECORD_OFFSET = DEFAULT_RECORD_SIZE;
  private static final int NEXT_RECORD_OFFSET = PREV_RECORD_OFFSET + 4;
  private static final int TIMESTAMP_OFFSET = NEXT_RECORD_OFFSET + 4;
  private static final int RECORD_SIZE = TIMESTAMP_OFFSET + 8;

  private static final byte[] ZEROS = new byte[RECORD_SIZE];

  public LocalHistoryRecordsTable(@NotNull Path storageFilePath, @NotNull StorageLockContext pool) throws IOException {
    super(storageFilePath, pool);
  }

  @Override
  protected int getHeaderSize() {
    return HEADER_SIZE;
  }

  @Override
  protected int getRecordSize() {
    return RECORD_SIZE;
  }

  @Override
  protected int getImplVersion() {
    return VERSION;
  }

  @Override
  protected byte[] getZeros() {
    return ZEROS;
  }

  public long getLastId() throws IOException {
    return myStorage.getLong(LAST_ID_OFFSET);
  }

  public void setLastId(long lastId) throws IOException {
    markDirty();
    myStorage.putLong(LAST_ID_OFFSET, lastId);
  }

  public void setFirstRecord(int record) throws IOException {
    markDirty();
    myStorage.putInt(FIRST_RECORD_OFFSET, record);
  }

  public int getFirstRecord() throws IOException {
    return myStorage.getInt(FIRST_RECORD_OFFSET);
  }

  public void setLastRecord(int record) throws IOException {
    markDirty();
    myStorage.putInt(LAST_RECORD_OFFSET, record);
  }

  public int getLastRecord() throws IOException {
    return myStorage.getInt(LAST_RECORD_OFFSET);
  }

  public void setFSTimestamp(long timestamp) throws IOException {
    markDirty();
    myStorage.putLong(FS_TIMESTAMP_OFFSET, timestamp);
  }

  public long getFSTimestamp() throws IOException {
    return myStorage.getLong(FS_TIMESTAMP_OFFSET);
  }

  public void setPrevRecord(int record, int prevRecord) throws IOException {
    markDirty();
    myStorage.putInt(getOffset(record, PREV_RECORD_OFFSET), prevRecord);
  }

  public int getPrevRecord(int record) throws IOException {
    return myStorage.getInt(getOffset(record, PREV_RECORD_OFFSET));
  }

  public void setNextRecord(int record, int nextRecord) throws IOException {
    markDirty();
    myStorage.putInt(getOffset(record, NEXT_RECORD_OFFSET), nextRecord);
  }

  public int getNextRecord(int record) throws IOException {
    return myStorage.getInt(getOffset(record, NEXT_RECORD_OFFSET));
  }

  public void setTimestamp(int record, long timestamp) throws IOException {
    markDirty();
    myStorage.putLong(getOffset(record, TIMESTAMP_OFFSET), timestamp);
  }

  public long getTimestamp(int record) throws IOException {
    return myStorage.getLong(getOffset(record, TIMESTAMP_OFFSET));
  }
}

