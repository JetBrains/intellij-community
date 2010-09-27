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

import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.utils.LocalHistoryLog;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.Consumer;
import com.intellij.util.io.storage.AbstractStorage;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

public class ChangeListStorageImpl implements ChangeListStorage {
  private static final int VERSION = 5;
  private static final String STORAGE_FILE = "changes";

  private final LocalHistoryStorage myStorage;

  public ChangeListStorageImpl(File storageDir) {
    try {
      myStorage = createStorage(storageDir);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  private static LocalHistoryStorage createStorage(File storageDir) throws IOException {
    String path = storageDir.getPath() + "/" + STORAGE_FILE;
    LocalHistoryStorage result = new LocalHistoryStorage(path);

    long fsTimestamp = ((PersistentFS)ManagingFS.getInstance()).getCreationTimestamp();

    int storedVersion = result.getVersion();
    boolean versionMismatch = storedVersion != VERSION;
    boolean timestampMismatch = result.getFSTimestamp() != fsTimestamp;
    if (versionMismatch || timestampMismatch) {
      if (versionMismatch) {
        LocalHistoryLog.LOG.info(MessageFormat.format(
          "local history version mismatch (was: {0}, expected: {1}), rebuilding...", storedVersion, VERSION));
      }
      if (timestampMismatch) LocalHistoryLog.LOG.info("FS has been rebuild, rebuilding local history...");
      result.dispose();
      if (!FileUtil.delete(storageDir)) {
        throw new IOException("cannot clear storage dir: " + storageDir);
      }
      result = new LocalHistoryStorage(path);
      result.setVersion(VERSION);
      result.setFSTimestamp(fsTimestamp);
    }
    return result;
  }

  private RuntimeException handleError(Throwable e) {
    try {
      if (myStorage != null) {
        myStorage.setVersion(-1);
        myStorage.force();
      }
    }
    catch (Throwable ex) {
      LocalHistoryLog.LOG.error("cannot mark storage as broken", ex);
    }
    throw new RuntimeException("Local history is broken and will be rebuilt after restart (storage version: " + VERSION + ")", e);
  }

  public synchronized void close() {
    myStorage.dispose();
  }

  public synchronized long nextId() {
    return myStorage.nextId();
  }

  public synchronized ChangeSetHolder readPrevious(int id) {
    int prevId = id == -1 ? myStorage.getLastRecord() : myStorage.getPrevRecord(id);
    if (prevId == 0) return null;

    return doReadBlock(prevId);
  }

  private ChangeSetHolder doReadBlock(int id) {
    try {
      DataInputStream in = myStorage.readStream(id);
      try {
        return new ChangeSetHolder(id, new ChangeSet(in));
      }
      finally {
        in.close();
      }
    }
    catch (Throwable e) {
      throw handleError(e);
    }
  }

  public synchronized void writeNextSet(ChangeSet changeSet) {
    try {
      int id = myStorage.createNextRecord();
      AbstractStorage.StorageDataOutput out = myStorage.writeStream(id);
      try {
        changeSet.write(out);
      }
      finally {
        out.close();
      }
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  public synchronized void purge(long period, int intervalBetweenActivities, Consumer<ChangeSet> processor) {
    int eachBlockId = findFirstObsoleteBlock(period, intervalBetweenActivities);
    try {
      while (eachBlockId != 0) {
        processor.consume(doReadBlock(eachBlockId).changeSet);
        int toDelete = eachBlockId;
        eachBlockId = myStorage.getPrevRecord(eachBlockId);
        myStorage.deleteRecord(toDelete);
      }
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  private int findFirstObsoleteBlock(long period, int intervalBetweenActivities) {
    long prevTimestamp = 0;
    long length = 0;

    int last = myStorage.getLastRecord();
    while (last != 0) {
      long t = myStorage.getTimestamp(last);
      if (prevTimestamp == 0) prevTimestamp = t;

      long delta = prevTimestamp - t;
      prevTimestamp = t;

      // we sum only intervals between changes during one 'day' (intervalBetweenActivities) and add '1' between two 'days'
      length += delta < intervalBetweenActivities ? delta : 1;

      if (length >= period) return last;

      last = myStorage.getPrevRecord(last);
    }

    return 0;
  }

  public synchronized void flush() {
    myStorage.flushSome();
  }
}