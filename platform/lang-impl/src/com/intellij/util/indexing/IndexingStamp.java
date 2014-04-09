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

package com.intellij.util.indexing;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 25, 2007
 *
 * A file has three indexed states (per particular index): indexed (with particular index_stamp), outdated and (trivial) unindexed
 * if index version is advanced or we rebuild it then index_stamp is advanced, we rebuild everything
 * if we get remove file event -> we should remove all indexed state from indices data for it (if state is nontrivial)
 * and set its indexed state to unindexed
 * if we get other event we set indexed state to outdated
 *
 * Index stamp is file timestamp of the index directory, it is assumed that index stamps are monotonically increasing, but
 * still << Long.MAX_VALUE: there are two negative special timestamps used for marking outdated / unindexed index state.
 * The code doesn't take overflow of real file timestaps (or their coincidence to negative special timestamps) into account because
 * it will happen (if time will go as forward as it does today) near year 292277094 (=new java.util.Date(Long.MAX_VALUE).getYear()).
 * At that time (if this code will be still actual) we can use positive small timestamps for special cases.
 */
public class IndexingStamp {
  private static final long UNINDEXED_STAMP = -1L; // we don't store trivial "absent" state
  private static final long INDEX_DATA_OUTDATED_STAMP = -2L;

  private static final int VERSION = 10;
  private static final ConcurrentHashMap<ID<?, ?>, Long> ourIndexIdToCreationStamp = new ConcurrentHashMap<ID<?, ?>, Long>();
  private static volatile long ourLastStamp; // ensure any file index stamp increases

  private IndexingStamp() {}

  public static synchronized void rewriteVersion(@NotNull final File file, final int version) throws IOException {
    final long prevLastModifiedValue = file.lastModified();
    if (file.exists()) {
      FileUtil.delete(file);
    }
    file.getParentFile().mkdirs();
    final DataOutputStream os = FileUtilRt.doIOOperation(new FileUtilRt.RepeatableIOOperation<DataOutputStream, FileNotFoundException>() {
      @Nullable
      @Override
      public DataOutputStream execute(boolean lastAttempt) throws FileNotFoundException {
        try {
          return new DataOutputStream(new FileOutputStream(file));
        }
        catch (FileNotFoundException ex) {
          if (lastAttempt) throw ex;
          return null;
        }
      }
    });
    assert os != null;
    try {
      os.writeInt(version);
      os.writeInt(VERSION);
    }
    finally {
      ourIndexIdToCreationStamp.clear();
      os.close();
      long max = Math.max(System.currentTimeMillis(), Math.max(prevLastModifiedValue, ourLastStamp) + 2000);
      ourLastStamp = max;
      file.setLastModified(max);
    }
  }

  public static boolean versionDiffers(@NotNull File versionFile, final int currentIndexVersion) {
    try {
      ourLastStamp = Math.max(ourLastStamp, versionFile.lastModified());
      final DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(versionFile)));
      try {
        final int savedIndexVersion = in.readInt();
        final int commonVersion = in.readInt();
        return savedIndexVersion != currentIndexVersion || commonVersion != VERSION;
      }
      finally {
        in.close();
      }
    }
    catch (IOException e) {
      return true;
    }
  }

  private static long getIndexCreationStamp(@NotNull ID<?, ?> indexName) {
    Long version = ourIndexIdToCreationStamp.get(indexName);
    if (version != null) return version.longValue();

    long stamp = IndexInfrastructure.getVersionFile(indexName).lastModified();
    ourIndexIdToCreationStamp.putIfAbsent(indexName, stamp);

    return stamp;
  }

  public static boolean isFileIndexedStateCurrent(VirtualFile file, ID<?, ?> indexName) {
    try {
      return getIndexStamp(file, indexName) == getIndexCreationStamp(indexName);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (!(cause instanceof IOException)) {
        throw e; // in case of IO exceptions consider file unindexed
      }
    }

    return false;
  }

  public static void setFileIndexedStateCurrent(VirtualFile file, ID<?, ?> id) {
    update(file, id, getIndexCreationStamp(id));
  }

  public static void setFileIndexedStateUnindexed(VirtualFile file, ID<?, ?> id) {
    update(file, id, UNINDEXED_STAMP);
  }

  public static void setFileIndexedStateOutdated(VirtualFile file, ID<?, ?> id) {
    update(file, id, INDEX_DATA_OUTDATED_STAMP);
  }

  /**
   * The class is meant to be accessed from synchronized block only 
   */
  private static class Timestamps {
    private static final FileAttribute PERSISTENCE = new FileAttribute("__index_stamps__", 2, false);
    private TObjectLongHashMap<ID<?, ?>> myIndexStamps;
    private boolean myIsDirty = false;

    private Timestamps(@Nullable DataInputStream stream) throws IOException {
      if (stream != null) {
        try {
          int[] outdatedIndices = null;
          long dominatingIndexStamp = DataInputOutputUtil.readTIME(stream);
          long diff = dominatingIndexStamp - DataInputOutputUtil.timeBase;
          if (diff != 0 && diff < ID.MAX_NUMBER_OF_INDICES) {
            int numberOfOutdatedIndices = (int)diff;
            outdatedIndices = new int[numberOfOutdatedIndices];
            while(numberOfOutdatedIndices > 0) {
              outdatedIndices[--numberOfOutdatedIndices] = DataInputOutputUtil.readINT(stream);
            }
            dominatingIndexStamp = DataInputOutputUtil.readTIME(stream);
          }

          while(stream.available() > 0) {
            ID<?, ?> id = ID.findById(DataInputOutputUtil.readINT(stream));
            if (id != null) {
              long stamp = getIndexCreationStamp(id);
              if (myIndexStamps == null) myIndexStamps = new TObjectLongHashMap<ID<?, ?>>(5, 0.98f);
              if (stamp <= dominatingIndexStamp) myIndexStamps.put(id, stamp);
            }
          }

          if (outdatedIndices != null) {
            for(int outdatedIndexId:outdatedIndices) {
              ID<?, ?> id = ID.findById(outdatedIndexId);
              if (id != null) {
                long stamp = INDEX_DATA_OUTDATED_STAMP;
                if (myIndexStamps == null) myIndexStamps = new TObjectLongHashMap<ID<?, ?>>(5, 0.98f);
                if (stamp <= dominatingIndexStamp) myIndexStamps.put(id, stamp);
              }
            }
          }
        }
        finally {
          stream.close();
        }
      }
    }

    // Indexed stamp compact format:
    // (DataInputOutputUtil.timeBase + numberOfOutdatedIndices outdated_index_id+)? (dominating_index_stamp) index_id*
    // Note, that FSRecords.REASONABLY_SMALL attribute storage allocation policy will give an attribute 32 bytes to each file
    // Compact format allows 22 indexed states in this state
    private void writeToStream(final DataOutputStream stream) throws IOException {
      if (myIndexStamps != null && !myIndexStamps.isEmpty()) {
        final long[] data = new long[2];
        final int dominatingStampIndex = 0;
        final int numberOfOutdatedIndex = 1;
        myIndexStamps.forEachEntry(
          new TObjectLongProcedure<ID<?, ?>>() {
            @Override
            public boolean execute(ID<?, ?> a, long b) {
              if (b == INDEX_DATA_OUTDATED_STAMP) {
                ++data[numberOfOutdatedIndex];
                b = getIndexCreationStamp(a);
              }
              data[dominatingStampIndex] = Math.max(data[dominatingStampIndex], b);

              return true;
            }
          }
        );
        if (data[numberOfOutdatedIndex] > 0) {
          assert data[numberOfOutdatedIndex] < ID.MAX_NUMBER_OF_INDICES;
          DataInputOutputUtil.writeTIME(stream, DataInputOutputUtil.timeBase + data[numberOfOutdatedIndex]);
          myIndexStamps.forEachEntry(new TObjectLongProcedure<ID<?, ?>>() {
            @Override
            public boolean execute(final ID<?, ?> id, final long timestamp) {
              try {
                if (timestamp == INDEX_DATA_OUTDATED_STAMP) {
                  DataInputOutputUtil.writeINT(stream, id.getUniqueId());
                }
                return true;
              }
              catch (IOException e) {
                throw new RuntimeException(e);
              }
            }
          });
        }
        DataInputOutputUtil.writeTIME(stream, data[dominatingStampIndex]);
        myIndexStamps.forEachEntry(new TObjectLongProcedure<ID<?, ?>>() {
          @Override
          public boolean execute(final ID<?, ?> id, final long timestamp) {
            try {
              if (timestamp == INDEX_DATA_OUTDATED_STAMP) return true;
              DataInputOutputUtil.writeINT(stream, id.getUniqueId());
              return true;
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        });
      }
      else {
        DataInputOutputUtil.writeTIME(stream, DataInputOutputUtil.timeBase);
      }
    }

    private long get(ID<?, ?> id) {
      return myIndexStamps != null? myIndexStamps.get(id) : 0L;
    }

    private void set(ID<?, ?> id, long tmst) {
      try {
        if (tmst == UNINDEXED_STAMP) {
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

  private static final ConcurrentMap<VirtualFile, Timestamps> myTimestampsCache = new ConcurrentHashMap<VirtualFile, Timestamps>();
  private static final BlockingQueue<VirtualFile> ourFinishedFiles = new ArrayBlockingQueue<VirtualFile>(100);

  public static long getIndexStamp(@NotNull VirtualFile file, ID<?, ?> indexName) {
    synchronized (getStripedLock(file)) {
      Timestamps stamp = createOrGetTimeStamp(file);
      if (stamp != null) return stamp.get(indexName);
      return 0;
    }
  }

  private static Timestamps createOrGetTimeStamp(@NotNull VirtualFile file) {
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

  public static void update(@NotNull VirtualFile file, @NotNull ID<?, ?> indexName, final long indexCreationStamp) {
    synchronized (getStripedLock(file)) {
      try {
        Timestamps stamp = createOrGetTimeStamp(file);
        if (stamp != null) stamp.set(indexName, indexCreationStamp);
      }
      catch (InvalidVirtualFileAccessException ignored /*ok to ignore it here*/) {
      }
    }
  }

  @NotNull
  public static List<ID<?,?>> getNontrivialFileIndexedStates(@NotNull VirtualFile file) {
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
  }

  public static void flushCache(@Nullable VirtualFile finishedFile) {
    while (finishedFile == null || !ourFinishedFiles.offer(finishedFile)) {
      List<VirtualFile> files = new ArrayList<VirtualFile>(ourFinishedFiles.size());
      ourFinishedFiles.drainTo(files);

      if (!files.isEmpty()) {
        for (VirtualFile file : files) {
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
      if (finishedFile == null) break;
      // else repeat until ourFinishedFiles.offer() succeeds
    }
  }

  private static final Object[] ourLocks = new Object[16];
  static {
    for(int i = 0; i < ourLocks.length; ++i) ourLocks[i] = new Object();
  }

  private static Object getStripedLock(@NotNull VirtualFile file) {
    if (!(file instanceof NewVirtualFile)) return 0;
    int id = ((NewVirtualFile)file).getId();
    return ourLocks[(id & 0xFF) % ourLocks.length];
  }
}
