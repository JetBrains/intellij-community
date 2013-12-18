/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.util.indexing;

import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.TObjectLongHashMap;
import gnu.trove.TObjectLongProcedure;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 25, 2007
 */
public class IndexingStamp {
  private IndexingStamp() {
  }

  /**
   * The class is meant to be accessed from synchronized block only 
   */
  private static class Timestamps {
    private static final FileAttribute PERSISTENCE = new FileAttribute("__index_stamps__", 1, false);
    private TObjectLongHashMap<ID<?, ?>> myIndexStamps;
    private boolean myIsDirty = false;

    private Timestamps() {
      myIsDirty = true;
    }

    private Timestamps(@Nullable DataInputStream stream) throws IOException {
      if (stream != null) {
        try {

          long dominatingIndexStamp = DataInputOutputUtil.readTIME(stream);
          while(stream.available() > 0) {
            ID<?, ?> id = ID.findById(DataInputOutputUtil.readINT(stream));
            if (id != null) {
              long stamp = IndexInfrastructure.getIndexCreationStamp(id);
              if (myIndexStamps == null) myIndexStamps = new TObjectLongHashMap<ID<?, ?>>(5, 0.98f);
              if (stamp <= dominatingIndexStamp) myIndexStamps.put(id, stamp);
            }
          }
        }
        finally {
          stream.close();
        }
      }
    }

    private void writeToStream(final DataOutputStream stream) throws IOException {
      if (myIndexStamps != null && !myIndexStamps.isEmpty()) {
        final long[] dominatingIndexStamp = new long[1];
        myIndexStamps.forEachEntry(
          new TObjectLongProcedure<ID<?, ?>>() {
            @Override
            public boolean execute(ID<?, ?> a, long b) {
              dominatingIndexStamp[0] = Math.max(dominatingIndexStamp[0], b);
              return true;
            }
          }
        );
        DataInputOutputUtil.writeTIME(stream, dominatingIndexStamp[0]);
        myIndexStamps.forEachEntry(new TObjectLongProcedure<ID<?, ?>>() {
          @Override
          public boolean execute(final ID<?, ?> id, final long timestamp) {
            try {
              DataInputOutputUtil.writeINT(stream, id.getUniqueId());
              return true;
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        });
      } else {
        DataInputOutputUtil.writeTIME(stream, DataInputOutputUtil.timeBase);
      }
    }

    public long get(ID<?, ?> id) {
      return myIndexStamps != null? myIndexStamps.get(id) : 0L;
    }

    public void set(ID<?, ?> id, long tmst) {
      try {
        if (tmst < 0) {
          if (myIndexStamps == null) return;
          myIndexStamps.remove(id);
          return;
        }
        if (myIndexStamps == null) myIndexStamps = new TObjectLongHashMap<ID<?, ?>>(5, 0.98f);

        myIndexStamps.put(id, tmst);
      }
      finally {
        myIsDirty = true;
      }
    }

    public boolean isDirty() {
      return myIsDirty;
    }
  }

  private static final ConcurrentHashMap<VirtualFile, Timestamps> myTimestampsCache = new ConcurrentHashMap<VirtualFile, Timestamps>();
  private static final int CAPACITY = 100;
  private static final ArrayBlockingQueue<VirtualFile> myFinishedFiles = new ArrayBlockingQueue<VirtualFile>(CAPACITY);

  public static boolean isFileIndexed(VirtualFile file, ID<?, ?> indexName, final long indexCreationStamp) {
    try {
      return getIndexStamp(file, indexName) == indexCreationStamp;
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (!(cause instanceof IOException)) {
        throw e; // in case of IO exceptions consider file unindexed
      }
    }

    return false;
  }

  public static long getIndexStamp(VirtualFile file, ID<?, ?> indexName) {
    synchronized (getStripedLock(file)) {
      Timestamps stamp = createOrGetTimeStamp(file);
      if (stamp != null) return stamp.get(indexName);
      return 0;
    }
  }

  private static Timestamps createOrGetTimeStamp(VirtualFile file) {
    if (file instanceof NewVirtualFile && file.isValid()) {
      Timestamps timestamps = myTimestampsCache.get(file);
      if (timestamps == null) {
        final DataInputStream stream = Timestamps.PERSISTENCE.readAttribute(file);
        try {
          timestamps = new Timestamps(stream);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        myTimestampsCache.put(file, timestamps);
      }
      return timestamps;
    }
    return null;
  }

  public static void update(final VirtualFile file, final ID<?, ?> indexName, final long indexCreationStamp) {
    synchronized (getStripedLock(file)) {
      try {
        Timestamps stamp = createOrGetTimeStamp(file);
        if (stamp != null) stamp.set(indexName, indexCreationStamp);
      }
      catch (InvalidVirtualFileAccessException ignored /*ok to ignore it here*/) {
      }
    }
  }

  public static void removeAllIndexedState(VirtualFile file) {
    synchronized (getStripedLock(file)) {
      if (file instanceof NewVirtualFile && file.isValid()) {
        myTimestampsCache.put(file, new Timestamps());
      }
    }
  }

  public static Collection<ID<?,?>> getIndexedIds(final VirtualFile file) {
    synchronized (getStripedLock(file)) {
      try {
        Timestamps stamp = createOrGetTimeStamp(file);
        if (stamp != null && stamp.myIndexStamps != null && !stamp.myIndexStamps.isEmpty()) {
          final SmartList<ID<?, ?>> retained = new SmartList<ID<?, ?>>();
          stamp.myIndexStamps.forEach(new TObjectProcedure<ID<?, ?>>() {
            @Override
            public boolean execute(ID<?, ?> object) {
              retained.add(object);
              return true;
            }
          });
          return retained;
        }
      }
      catch (InvalidVirtualFileAccessException ignored /*ok to ignore it here*/) {
      }
    }
    return Collections.emptyList();
  }

  public static void flushCaches() {
    flushCache(null);
    myTimestampsCache.clear();
  }

  public static void flushCache(@Nullable VirtualFile finishedFile) {
    if (finishedFile == null || !myFinishedFiles.offer(finishedFile)) {
      VirtualFile[] files = null;
      synchronized (myFinishedFiles) {
        int size = myFinishedFiles.size();
        if ((finishedFile == null && size > 0) || size == CAPACITY) {
          files = myFinishedFiles.toArray(new VirtualFile[size]);
          myFinishedFiles.clear();
        }
      }

      if (files != null) {
        for(VirtualFile file:files) {
          synchronized (getStripedLock(file)) {
            Timestamps timestamp = myTimestampsCache.remove(file);
            if (timestamp == null) continue;
            try {
              if (timestamp.isDirty() && file.isValid()) {
                final DataOutputStream sink = Timestamps.PERSISTENCE.writeAttribute(file);
                timestamp.writeToStream(sink);
                sink.close();
              }
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        }
      }
      if (finishedFile != null) myFinishedFiles.offer(finishedFile);
    }
  }

  private static final Object[] ourLocks = new Object[16];
  static {
    for(int i = 0; i < ourLocks.length; ++i) ourLocks[i] = new Object();
  }

  private static Object getStripedLock(VirtualFile file) {
    if (!(file instanceof NewVirtualFile)) return 0;
    int id = ((NewVirtualFile)file).getId();
    return ourLocks[(id & 0xFF) % ourLocks.length];
  }
}
