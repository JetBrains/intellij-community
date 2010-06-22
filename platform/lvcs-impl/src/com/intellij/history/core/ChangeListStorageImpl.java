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
import java.util.ArrayList;
import java.util.List;

public class ChangeListStorageImpl implements ChangeListStorage {
  private static final int VERSION = 3;

  private static final String STORAGE_FILE = "changes";

  private final File myStorageDir;
  private final LinkedStorage myStorage;

  public ChangeListStorageImpl(File storageDir) {
    myStorageDir = storageDir;
    try {
      myStorage = createStorage(myStorageDir);
    }
    catch (IOException e) {
      throw handleError(e);
    }
  }

  private static LinkedStorage createStorage(File storageDir) throws IOException {
    String path = storageDir.getPath() + "/" + STORAGE_FILE;
    LinkedStorage result = new LinkedStorage(path);

    long fsTimestamp = ((PersistentFS)ManagingFS.getInstance()).getCreationTimestamp();

    boolean versionMismatch = result.getVersion() != VERSION;
    boolean timestampMismatch = result.getFSTimestamp() != fsTimestamp;
    if (versionMismatch || timestampMismatch) {
      if (versionMismatch) LocalHistoryLog.LOG.info("local history version mismatch, rebuilding...");
      if (timestampMismatch) LocalHistoryLog.LOG.info("FS has been rebuild, rebuilding clearing local history...");
      result.dispose();
      if (!FileUtil.delete(storageDir)) {
        throw new IOException("cannot clear storage dir: " + storageDir);
      }
      result = new LinkedStorage(path);
      result.setVersion(VERSION);
      result.setFSTimestamp(fsTimestamp);
    }
    return result;
  }

  private RuntimeException handleError(Throwable e) {
    try {
      if (myStorage != null) {
        myStorage.setVersion(-1);
      }
    }
    catch (Throwable ex) {
      LocalHistoryLog.LOG.error("cannot mark storage as broken", ex);
    }
    throw new RuntimeException(e);
  }

  public synchronized void close() {
    myStorage.dispose();
  }

  public synchronized long nextId() {
    return myStorage.nextId();
  }

  public synchronized ChangeSetBlock createNewBlock() {
    return new ChangeSetBlock(0);
  }

  public synchronized ChangeSetBlock readPrevious(ChangeSetBlock block) {
    int prevId = block.id == 0 ? myStorage.getLastRecord() : myStorage.getPrevRecord(block.id);
    if (prevId == 0) return null;
    assert prevId != block.id;
    return doReadBlock(prevId);
  }

  private ChangeSetBlock doReadBlock(int id) {
    DataInputStream in = myStorage.readStream(id);
    try {
      try {
        int size = in.readInt();
        List<ChangeSet> changes = new ArrayList<ChangeSet>(size);
        while (size-- > 0) {
          changes.add(new ChangeSet(in));
        }
        return new ChangeSetBlock(id, changes);
      }
      finally {
        in.close();
      }
    }
    catch (Throwable e) {
      throw handleError(e);
    }
  }

  public synchronized void writeNextBlock(ChangeSetBlock block) {
    try {
      block.id = myStorage.createNextRecord();
      AbstractStorage.StorageDataOutput out = myStorage.writeStream(block.id);
      try {
        out.writeInt(block.changes.size());
        for (ChangeSet each : block.changes) {
          each.write(out);
        }
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
      while(eachBlockId != 0) {
        for (ChangeSet eachChangeSet : doReadBlock(eachBlockId).changes) {
          processor.consume(eachChangeSet);
        }
        myStorage.deleteRecord(eachBlockId);
        eachBlockId = myStorage.getPrevRecord(eachBlockId);
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