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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.util.SmartList;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 25, 2007
 *
 * A file has three indexed states (per particular index): indexed (with particular index_stamp), outdated and (trivial) unindexed
 * if index version is advanced or we rebuild it then index_stamp is advanced, we rebuild everything
 * if we get remove file event -> we should remove all indexed state from indices data for it (if state is nontrivial)
 * and set its indexed state to outdated
 * if we get other event we set indexed state to outdated
 *
 * Index stamp is file modified timestamp of the index's version file, it is assumed that index stamps are monotonically increasing, but
 * still << Long.MAX_VALUE: there is one negative special timestamp used for marking outdated index state.
 * The code doesn't take overflow of real file timestamps (or their coincidence to negative special timestamps) into account because
 * it will happen (if time will go as forward as it does today) near year 292277094 (=new java.util.Date(Long.MAX_VALUE).getYear()).
 * At that time (if this code will be still actual) we can use positive small timestamps for special cases.
 */
public class IndexingStamp {
  private static final long INDEX_DATA_OUTDATED_STAMP = -2L;

  private static final int VERSION = 15;
  private static final ConcurrentMap<ID<?, ?>, Long> ourIndexIdToCreationStamp = ContainerUtil.newConcurrentMap();
  static final int INVALID_FILE_ID = 0;
  private static volatile long ourLastStamp; // ensure any file index stamp increases

  private IndexingStamp() {}

  public static synchronized void rewriteVersion(@NotNull final File file, final int version) throws IOException {
    SharedIndicesData.beforeSomeIndexVersionInvalidation();
    final long prevLastModifiedValue = file.lastModified();
    if (file.exists()) {
      FileUtil.deleteWithRenaming(file);
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
      DataInputOutputUtil.writeINT(os, version);
      DataInputOutputUtil.writeINT(os, VERSION);
      DataInputOutputUtil.writeTIME(os, FSRecords.getCreationTimestamp());
    }
    finally {
      ourIndexIdToCreationStamp.clear();
      os.close();
      long max = Math.max(
        System.currentTimeMillis(),
        Math.max(prevLastModifiedValue + MIN_FS_MODIFIED_TIMESTAMP_RESOLUTION, ourLastStamp + OUR_INDICES_TIMESTAMP_INCREMENT)
      );
      ourLastStamp = max;
      final boolean lastModifiedSuccess = file.setLastModified(max);
      if (!lastModifiedSuccess) {
        Logger.getInstance(IndexingStamp.class).info("Setting lastModified failed for " + file + " timestamp:" + max);
        ourLastStamp = Math.max(ourLastStamp, file.lastModified());
      }
    }
  }

  private static final int MIN_FS_MODIFIED_TIMESTAMP_RESOLUTION = 2000; // https://en.wikipedia.org/wiki/File_Allocation_Table,
  // 1s for ext3 / hfs+ http://unix.stackexchange.com/questions/11599/determine-file-system-timestamp-accuracy
  // https://en.wikipedia.org/wiki/HFS_Plus

  private static final int OUR_INDICES_TIMESTAMP_INCREMENT = SystemProperties.getIntProperty("idea.indices.timestamp.resolution", 1);

  public static boolean versionDiffers(@NotNull File versionFile, final int currentIndexVersion) {
    try {
      ourLastStamp = Math.max(ourLastStamp, versionFile.lastModified());
      final DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(versionFile)));
      try {
        final int savedIndexVersion = DataInputOutputUtil.readINT(in);
        final int commonVersion = DataInputOutputUtil.readINT(in);
        final long vfsCreationStamp = DataInputOutputUtil.readTIME(in);
        return savedIndexVersion != currentIndexVersion ||
               commonVersion != VERSION ||
               vfsCreationStamp != FSRecords.getCreationTimestamp()
          ;

      }
      finally {
        in.close();
      }
    }
    catch (IOException e) {
      return true;
    }
  }

  public static long getIndexCreationStamp(@NotNull ID<?, ?> indexName) {
    Long version = ourIndexIdToCreationStamp.get(indexName);
    if (version != null) return version.longValue();

    long stamp = IndexInfrastructure.getVersionFile(indexName).lastModified();
    ourIndexIdToCreationStamp.putIfAbsent(indexName, stamp);

    return stamp;
  }

  public static boolean isFileIndexedStateCurrent(int fileId, ID<?, ?> indexName) {
    try {
      return getIndexStamp(fileId, indexName) == getIndexCreationStamp(indexName);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (!(cause instanceof IOException)) {
        throw e; // in case of IO exceptions consider file unindexed
      }
    }

    return false;
  }

  public static void setFileIndexedStateCurrent(int fileId, ID<?, ?> id) {
    update(fileId, id, getIndexCreationStamp(id));
  }

  public static void setFileIndexedStateOutdated(int fileId, ID<?, ?> id) {
    update(fileId, id, INDEX_DATA_OUTDATED_STAMP);
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
          if (diff > 0 && diff < ID.MAX_NUMBER_OF_INDICES) {
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
              if (stamp == 0) continue; // All (indices) IDs should be valid in this running session (e.g. we can have ID instance existing but index is not registered)
              if (myIndexStamps == null) myIndexStamps = new TObjectLongHashMap<>(5, 0.98f);
              if (stamp <= dominatingIndexStamp) myIndexStamps.put(id, stamp);
            }
          }

          if (outdatedIndices != null) {
            for(int outdatedIndexId:outdatedIndices) {
              ID<?, ?> id = ID.findById(outdatedIndexId);
              if (id != null) {
                long stamp = INDEX_DATA_OUTDATED_STAMP;
                if (myIndexStamps == null) myIndexStamps = new TObjectLongHashMap<>(5, 0.98f);
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
      if (myIndexStamps == null) myIndexStamps = new TObjectLongHashMap<>(5, 0.98f);

      if (tmst == INDEX_DATA_OUTDATED_STAMP && !myIndexStamps.contains(id)) {
        return;
      }
      long previous = myIndexStamps.put(id, tmst);
      if (previous != tmst) myIsDirty = true;
    }

    public boolean isDirty() {
      return myIsDirty;
    }
  }

  private static final ConcurrentIntObjectMap<Timestamps> myTimestampsCache = ContainerUtil.createConcurrentIntObjectMap();
  private static final BlockingQueue<Integer> ourFinishedFiles = new ArrayBlockingQueue<>(100);

  public static long getIndexStamp(int fileId, ID<?, ?> indexName) {
    Lock readLock = getStripedLock(fileId).readLock();
    readLock.lock();
    try {
      Timestamps stamp = createOrGetTimeStamp(fileId);
      if (stamp != null) return stamp.get(indexName);
      return 0;
    } finally {
      readLock.unlock();
    }
  }

  private static Timestamps createOrGetTimeStamp(int id) {
    boolean isValid = id > 0;
    if (!isValid) {
      id = -id;
    }
    Timestamps timestamps = myTimestampsCache.get(id);
    if (timestamps == null) {
      final DataInputStream stream = FSRecords.readAttributeWithLock(id, Timestamps.PERSISTENCE);
      try {
        timestamps = new Timestamps(stream);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      if (isValid) myTimestampsCache.put(id, timestamps);
    }
    return timestamps;
  }

  public static void update(int fileId, @NotNull ID<?, ?> indexName, final long indexCreationStamp) {
    if (fileId < 0 || fileId == INVALID_FILE_ID) return;
    Lock writeLock = getStripedLock(fileId).writeLock();
    writeLock.lock();
    try {
      Timestamps stamp = createOrGetTimeStamp(fileId);
      if (stamp != null) stamp.set(indexName, indexCreationStamp);
    } finally {
      writeLock.unlock();
    }
  }

  @NotNull
  public static List<ID<?,?>> getNontrivialFileIndexedStates(int fileId) {
    if (fileId != INVALID_FILE_ID) {
      Lock readLock = getStripedLock(fileId).readLock();
      readLock.lock();
      try {
        Timestamps stamp = createOrGetTimeStamp(fileId);
        if (stamp != null && stamp.myIndexStamps != null && !stamp.myIndexStamps.isEmpty()) {
          final SmartList<ID<?, ?>> retained = new SmartList<>();
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
      finally {
        readLock.unlock();
      }
    }
    return Collections.emptyList();
  }

  public static void flushCaches() {
    flushCache((Integer)null);
  }

  public static void flushCache(@Nullable Integer finishedFile) {
    if (finishedFile != null && finishedFile == INVALID_FILE_ID) finishedFile = 0;
    // todo make better (e.g. FinishedFiles striping, remove integers)
    while (finishedFile == null || !ourFinishedFiles.offer(finishedFile)) {
      List<Integer> files = new ArrayList<>(ourFinishedFiles.size());
      ourFinishedFiles.drainTo(files);

      if (!files.isEmpty()) {
        for (Integer file : files) {
          Lock writeLock = getStripedLock(file).writeLock();
          writeLock.lock();
          try {
            Timestamps timestamp = myTimestampsCache.remove(file);
            if (timestamp == null) continue;

            if (timestamp.isDirty() /*&& file.isValid()*/) {
              final DataOutputStream sink = FSRecords.writeAttribute(file, Timestamps.PERSISTENCE);
              timestamp.writeToStream(sink);
              sink.close();
            }
          } catch (IOException e) {
            throw new RuntimeException(e);
          } finally {
            writeLock.unlock();
          }
        }
      }
      if (finishedFile == null) break;
      // else repeat until ourFinishedFiles.offer() succeeds
    }
  }

  private static final ReadWriteLock[] ourLocks = new ReadWriteLock[16];
  static {
    for(int i = 0; i < ourLocks.length; ++i) ourLocks[i] = new ReentrantReadWriteLock();
  }

  private static ReadWriteLock getStripedLock(int fileId) {
    if (fileId < 0) fileId = -fileId;
    return ourLocks[(fileId & 0xFF) % ourLocks.length];
  }
}
