// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.io.DataInputOutputUtil;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * A file has three indexed states (per particular index): indexed (with particular index_stamp which monotonically increases), outdated and (trivial) unindexed.
 * <ul>
 *   <li>If index version is advanced or we rebuild it then index_stamp is advanced, we rebuild everything.</li>
 *   <li>If we get remove file event -> we should remove all indexed state from indices data for it (if state is nontrivial)
 *  * and set its indexed state to outdated.</li>
 *   <li>If we get other event we set indexed state to outdated.</li>
 * </ul>
 */
public final class IndexingStamp {
  private static final boolean IS_UNIT_TEST = ApplicationManager.getApplication().isUnitTestMode();
  private static final long INDEX_DATA_OUTDATED_STAMP = -2L;
  private static final long HAS_NO_INDEXED_DATA_STAMP = 0L;

  private IndexingStamp() {}

  @NotNull
  public static FileIndexingState isFileIndexedStateCurrent(int fileId, @NotNull ID<?, ?> indexName) {
    try {
      long stamp = getIndexStamp(fileId, indexName);
      if (stamp == HAS_NO_INDEXED_DATA_STAMP) return FileIndexingState.NOT_INDEXED;
      return stamp == IndexVersion.getIndexCreationStamp(indexName) ? FileIndexingState.UP_TO_DATE : FileIndexingState.OUT_DATED;
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (!(cause instanceof IOException)) {
        throw e; // in case of IO exceptions consider file unindexed
      }
    }

    return FileIndexingState.OUT_DATED;
  }

  public static void setFileIndexedStateCurrent(int fileId, @NotNull ID<?, ?> id) {
    update(fileId, id, IndexVersion.getIndexCreationStamp(id));
  }

  public static void setFileIndexedStateOutdated(int fileId, @NotNull ID<?, ?> id) {
    update(fileId, id, INDEX_DATA_OUTDATED_STAMP);
  }

  public static void setFileIndexedStateUnindexed(int fileId, @NotNull ID<?, ?> id) {
    update(fileId, id, HAS_NO_INDEXED_DATA_STAMP);
  }

  /**
   * The class is meant to be accessed from synchronized block only
   */
  private static final class Timestamps {
    private static final FileAttribute PERSISTENCE = new FileAttribute("__index_stamps__", 2, false);
    private Object2LongMap<ID<?, ?>> myIndexStamps;
    private boolean myIsDirty = false;

    private Timestamps(@Nullable DataInputStream stream) throws IOException {
      if (stream != null) {
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
          if (id != null && !(id instanceof StubIndexKey)) {
            long stamp = IndexVersion.getIndexCreationStamp(id);
            if (stamp == 0) continue; // All (indices) IDs should be valid in this running session (e.g. we can have ID instance existing but index is not registered)
            if (myIndexStamps == null) {
              myIndexStamps = new Object2LongOpenHashMap<>(5, 0.98f);
            }
            if (stamp <= dominatingIndexStamp) {
              myIndexStamps.put(id, stamp);
            }
          }
        }

        if (outdatedIndices != null) {
          for(int outdatedIndexId:outdatedIndices) {
            ID<?, ?> id = ID.findById(outdatedIndexId);
            if (id != null && !(id instanceof StubIndexKey)) {
              if (IndexVersion.getIndexCreationStamp(id) == 0) continue; // All (indices) IDs should be valid in this running session (e.g. we can have ID instance existing but index is not registered)
              long stamp = INDEX_DATA_OUTDATED_STAMP;
              if (myIndexStamps == null) {
                myIndexStamps = new Object2LongOpenHashMap<>(5, 0.98f);
              }
              if (stamp <= dominatingIndexStamp) {
                myIndexStamps.put(id, stamp);
              }
            }
          }
        }
      }
    }

    // Indexed stamp compact format:
    // (DataInputOutputUtil.timeBase + numberOfOutdatedIndices outdated_index_id+)? (dominating_index_stamp) index_id*
    // Note, that FSRecords.REASONABLY_SMALL attribute storage allocation policy will give an attribute 32 bytes to each file
    // Compact format allows 22 indexed states in this state
    private void writeToStream(final DataOutputStream stream) throws IOException {
      if (myIndexStamps == null || myIndexStamps.isEmpty()) {
        DataInputOutputUtil.writeTIME(stream, DataInputOutputUtil.timeBase);
        return;
      }

      final long[] data = new long[2];
      final int dominatingStampIndex = 0;
      final int numberOfOutdatedIndex = 1;
      ObjectSet<Object2LongMap.Entry<ID<?, ?>>> entries = myIndexStamps.object2LongEntrySet();
      for (Object2LongMap.Entry<ID<?, ?>> entry : entries) {
        long b = entry.getLongValue();
        if (b == INDEX_DATA_OUTDATED_STAMP) {
           ++data[numberOfOutdatedIndex];
           b = IndexVersion.getIndexCreationStamp(entry.getKey());
         }
         data[dominatingStampIndex] = Math.max(data[dominatingStampIndex], b);

         if (IS_UNIT_TEST && b == HAS_NO_INDEXED_DATA_STAMP) {
           FileBasedIndexImpl.LOG.info("Wrong indexing timestamp state: " + myIndexStamps);
         }
      }

      if (data[numberOfOutdatedIndex] > 0) {
        assert data[numberOfOutdatedIndex] < ID.MAX_NUMBER_OF_INDICES;
        DataInputOutputUtil.writeTIME(stream, DataInputOutputUtil.timeBase + data[numberOfOutdatedIndex]);
        for (Object2LongMap.Entry<ID<?, ?>> entry : entries) {
          if (entry.getLongValue() == INDEX_DATA_OUTDATED_STAMP) {
            DataInputOutputUtil.writeINT(stream, entry.getKey().getUniqueId());
          }
        }
      }

      DataInputOutputUtil.writeTIME(stream, data[dominatingStampIndex]);
      for (Object2LongMap.Entry<ID<?, ?>> entry : entries) {
        if (entry.getLongValue() != INDEX_DATA_OUTDATED_STAMP) {
          DataInputOutputUtil.writeINT(stream, entry.getKey().getUniqueId());
        }
      }
    }

    private long get(ID<?, ?> id) {
      return myIndexStamps != null ? myIndexStamps.getLong(id) : HAS_NO_INDEXED_DATA_STAMP;
    }

    private void set(ID<?, ?> id, long tmst) {
      if (myIndexStamps == null) {
        myIndexStamps = new Object2LongOpenHashMap<>(5, 0.98f);
      }

      if (tmst == INDEX_DATA_OUTDATED_STAMP && !myIndexStamps.containsKey(id)) {
        return;
      }

      long previous = tmst == HAS_NO_INDEXED_DATA_STAMP ? myIndexStamps.removeLong(id) : myIndexStamps.put(id, tmst);
      if (previous != tmst) {
        myIsDirty = true;
      }
    }

    public boolean isDirty() {
      return myIsDirty;
    }
  }

  private static final int INDEXING_STAMP_CACHE_CAPACITY = SystemProperties.getIntProperty("index.timestamp.cache.size", 100);
  private static final ConcurrentIntObjectMap<Timestamps> ourTimestampsCache =
    ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  private static final BlockingQueue<Integer> ourFinishedFiles = new ArrayBlockingQueue<>(INDEXING_STAMP_CACHE_CAPACITY);

  static void dropTimestampMemoryCaches() {
    flushCaches();
    ourTimestampsCache.clear();
  }

  public static long getIndexStamp(int fileId, ID<?, ?> indexName) {
    return ourLock.withReadLock(fileId, () -> {
      Timestamps stamp = createOrGetTimeStamp(fileId);
      return stamp.get(indexName);
    });
  }

  @TestOnly
  public static void dropIndexingTimeStamps(int fileId) throws IOException {
    ourTimestampsCache.remove(fileId);
    try (DataOutputStream out =  FSRecords.writeAttribute(fileId, Timestamps.PERSISTENCE)) {
      new Timestamps(null).writeToStream(out);
    }
  }

  @NotNull
  private static Timestamps createOrGetTimeStamp(int id) {
    assert id > 0;
    Timestamps timestamps = ourTimestampsCache.get(id);
    if (timestamps == null) {
      try (final DataInputStream stream = FSRecords.readAttributeWithLock(id, Timestamps.PERSISTENCE)) {
        timestamps = new Timestamps(stream);
      }
      catch (IOException e) {
        FSRecords.handleError(e);
        throw new RuntimeException(e);
      }
      ourTimestampsCache.cacheOrGet(id, timestamps);
    }
    return timestamps;
  }

  public static void update(int fileId, @NotNull ID<?, ?> indexName, final long indexCreationStamp) {
    assert fileId > 0;
    ourLock.withWriteLock(fileId, () -> {
      Timestamps stamp = createOrGetTimeStamp(fileId);
      stamp.set(indexName, indexCreationStamp);
      return null;
    });
  }

  public static @NotNull List<ID<?, ?>> getNontrivialFileIndexedStates(int fileId) {
    return ourLock.withReadLock(fileId, () -> {
      try {
        Timestamps stamp = createOrGetTimeStamp(fileId);
        if (stamp.myIndexStamps != null && !stamp.myIndexStamps.isEmpty()) {
          return List.copyOf(stamp.myIndexStamps.keySet());
        }
      }
      catch (InvalidVirtualFileAccessException ignored /*ok to ignore it here*/) {
      }
      return Collections.emptyList();
    });
  }

  public static void flushCaches() {
    doFlush();
  }

  public static void flushCache(int finishedFile) {
    boolean exit = ourLock.withReadLock(finishedFile, () -> {
      Timestamps timestamps = ourTimestampsCache.get(finishedFile);
      if (timestamps == null) return true;
      if (!timestamps.isDirty()) {
        ourTimestampsCache.remove(finishedFile);
        return true;
      }
      return false;
    });
    if (exit) return;

    while (!ourFinishedFiles.offer(finishedFile)) {
      doFlush();
    }
  }

  @TestOnly
  public static int @NotNull [] dumpCachedUnfinishedFiles() {
    return ourLock.withAllLocksReadLocked(() -> {
      int[] cachedKeys = ourTimestampsCache.keys();
      if (cachedKeys.length == 0) {
        return ArrayUtil.EMPTY_INT_ARRAY;
      }
      else {
        IntSet cachedIds = new IntArraySet(cachedKeys);
        Set<Integer> finishedIds = new HashSet<>(ourFinishedFiles);
        cachedIds.removeAll(finishedIds);
        return cachedIds.toIntArray();
      }
    });
  }

  private static void doFlush() {
    List<Integer> files = new ArrayList<>(ourFinishedFiles.size());
    ourFinishedFiles.drainTo(files);

    if (!files.isEmpty()) {
      for (Integer file : files) {
        RuntimeException exception = ourLock.withWriteLock(file, () -> {
          try {
            Timestamps timestamp = ourTimestampsCache.remove(file);
            if (timestamp == null) return null;

            if (timestamp.isDirty() /*&& file.isValid()*/) {
              try (DataOutputStream sink = FSRecords.writeAttribute(file, Timestamps.PERSISTENCE)) {
                timestamp.writeToStream(sink);
              }
            }
            return null;
          }
          catch (IOException e) {
            return new RuntimeException(e);
          }
        });
        if (exception != null) {
          throw exception;
        }
      }
    }
  }

  private static final StripedLock ourLock = new StripedLock();
}