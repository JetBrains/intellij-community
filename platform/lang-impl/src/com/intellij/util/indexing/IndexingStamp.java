// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.indexing.impl.perFileVersion.AutoRefreshingOnVfsCloseRef;
import com.intellij.util.indexing.impl.perFileVersion.IntFileAttribute;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A file has three indexed states (per particular index): indexed (with particular index_stamp which monotonically increases), outdated and (trivial) unindexed.
 * <ul>
 *   <li>If index version is advanced or we rebuild it then index_stamp is advanced, we rebuild everything.</li>
 *   <li>If we get remove file event -> we should remove all indexed state from indices data for it (if state is nontrivial)
 *  * and set its indexed state to outdated.</li>
 *   <li>If we get other event we set indexed state to outdated.</li>
 * </ul>
 */
@Internal
public final class IndexingStamp {
  static final long INDEX_DATA_OUTDATED_STAMP = -2L;
  static final long HAS_NO_INDEXED_DATA_STAMP = 0L;

  private IndexingStamp() { }

  public static @NotNull FileIndexingState isFileIndexedStateCurrent(int fileId, @NotNull ID<?, ?> indexName) {
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

  public static void setFileIndexedStateCurrent(int fileId, @NotNull ID<?, ?> id, boolean isProvidedByInfrastructureExtension) {
    // TODO-ank: use isProvidedByInfrastructureExtension (DEA-334413)
    update(fileId, id, IndexVersion.getIndexCreationStamp(id));
  }

  public static void setFileIndexedStateOutdated(int fileId, @NotNull ID<?, ?> id) {
    update(fileId, id, INDEX_DATA_OUTDATED_STAMP);
  }

  public static void setFileIndexedStateUnindexed(int fileId, @NotNull ID<?, ?> id) {
    update(fileId, id, HAS_NO_INDEXED_DATA_STAMP);
  }

  private static final int INDEXING_STAMP_CACHE_CAPACITY = SystemProperties.getIntProperty("index.timestamp.cache.size", 100);
  //MAYBE RC: do we still need in-memory cache (fileId->Timestamps)? With new fast-attributes + fast enumerator
  //          access may be fast enough even without caching -- or, at least, it may be worth to cache enumerator
  //          records (which is 100-1000 records at max) _only_
  private static final ConcurrentIntObjectMap<Timestamps> ourTimestampsCache =
    ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  private static final BlockingQueue<Integer> ourFinishedFiles = new ArrayBlockingQueue<>(INDEXING_STAMP_CACHE_CAPACITY);

  private static final AutoRefreshingOnVfsCloseRef<IndexingStampStorage> storage =
    new AutoRefreshingOnVfsCloseRef<>(IndexingStamp::createStorage);

  // Read lock is used to flush caches. Write lock is to wait until all threads have finished flushing.
  // This is kind of abuse of RW lock. The goal ot to allow concurrent execution of flushCache(int finishedFile) from different threads.
  private static final ReadWriteLock flushLock = new ReentrantReadWriteLock();

  private static IndexingStampStorage createStorage(FSRecordsImpl unused) {
    if (IntFileAttribute.shouldUseFastAttributes()) {
      return new IndexingStampStorageOverFastAttributes();
    }
    else {
      return new IndexingStampStorageOverRegularAttributes();
    }
  }

  @TestOnly
  public static void dropTimestampMemoryCaches() {
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
    storage.invoke().writeTimestamps(fileId, TimestampsImmutable.EMPTY);
  }

  private static @NotNull Timestamps createOrGetTimeStamp(int id) {
    return getTimestamp(id, true);
  }

  @Contract("_, true->!null")
  private static Timestamps getTimestamp(int id, boolean createIfNoneSaved) {
    assert id > 0;
    Timestamps timestamps = ourTimestampsCache.get(id);
    if (timestamps == null) {
      TimestampsImmutable immutable = storage.invoke().readTimestamps(id);
      if (immutable == null) {
        if (createIfNoneSaved) {
          timestamps = new Timestamps();
        }
        else {
          return null;
        }
      }
      else {
        timestamps = immutable.toMutableTimestamps();
      }
    }
    ourTimestampsCache.cacheOrGet(id, timestamps);
    return timestamps;
  }

  @TestOnly
  public static boolean hasIndexingTimeStamp(int fileId) {
    Timestamps timestamp = getTimestamp(fileId, false);
    return timestamp != null && timestamp.hasIndexingTimeStamp();
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
        if (stamp.hasIndexingTimeStamp()) {
          return List.copyOf(stamp.getIndexIds());
        }
      }
      catch (InvalidVirtualFileAccessException ignored /*ok to ignore it here*/) {
      }
      return Collections.emptyList();
    });
  }

  public static void flushCaches() {
    doFlush();
    flushLock.writeLock().lock(); // wait until all doFlush in other threads are finished. TODO-ank: cooperate, not wait
    flushLock.writeLock().unlock();
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
    return ourLock.withAllLocksWriteLocked(() -> {
      int[] cachedKeys = ourTimestampsCache
        .entrySet()
        .stream()
        .filter(e -> e.getValue().isDirty())
        .mapToInt(e -> e.getKey())
        .toArray();

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
    flushLock.readLock().lock();
    try {
      List<Integer> files = new ArrayList<>(ourFinishedFiles.size());
      ourFinishedFiles.drainTo(files);

      if (!files.isEmpty()) {
        for (Integer fileId : files) {
          RuntimeException exception = ourLock.withWriteLock(fileId, () -> {
            try {
              final Timestamps timestamp = ourTimestampsCache.remove(fileId);
              if (timestamp == null) return null;

              if (timestamp.isDirty() /*&& file.isValid()*/) {
                //RC: now I don't see the benefits of implementing timestamps write via raw attribute bytebuffer access
                //    doFlush() is mostly outside the critical path, while implementing timestamps.writeToBuffer(buffer)
                //    is complicated with all those variable-sized numbers used.
                storage.invoke().writeTimestamps(fileId, timestamp.toImmutable());
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
    finally {
      flushLock.readLock().unlock();
    }
  }

  static boolean isDirty() {
    return !ourFinishedFiles.isEmpty();
  }

  private static final StripedLock ourLock = new StripedLock();

  static void close() {
    flushCaches();
    storage.close();
  }
}