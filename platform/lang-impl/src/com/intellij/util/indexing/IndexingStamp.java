// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl;
import com.intellij.util.ArrayUtil;
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
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.intellij.concurrency.ConcurrentCollectionFactory.createConcurrentIntObjectMap;
import static com.intellij.util.SystemProperties.getIntProperty;

/**
 * A file has three indexed states (per particular index): indexed (with particular index_stamp which monotonically increases), outdated and (trivial) unindexed.
 * <ul>
 *   <li>If the index version is advanced, or we rebuild it, then index_stamp is advanced, we rebuild everything.</li>
 *   <li>If we get a remove file event, then we should remove all indexed state from indices data for it (if state is nontrivial)
 *  * and set its indexed state to outdated.</li>
 *   <li>If we get another event we set indexed state to outdated.</li>
 * </ul>
 *
 * Since IndexingStamp contains only indexes modification count for each file, it can become outdated if a file was changed but the {@link Timestamps}
 * for the given file was not updated or not flushed on disk before IDE was terminated. In such case {@link IndexingFlag} can be used
 * to determine that a file needs to be reindexed as {@link IndexingFlag} contains file modification count when it was last indexed.
 */
@Internal
public final class IndexingStamp {
  public static final long INDEX_DATA_OUTDATED_STAMP = -2L;
  public static final long HAS_NO_INDEXED_DATA_STAMP = 0L;

  private IndexingStamp() { }

  public static @NotNull FileIndexingStateWithExplanation isFileIndexedStateCurrent(int fileId, @NotNull ID<?, ?> indexName) {
    try {
      long stamp = getIndexStamp(fileId, indexName);
      if (stamp == HAS_NO_INDEXED_DATA_STAMP) return FileIndexingStateWithExplanation.notIndexed();
      long indexCreationStamp = IndexVersion.getIndexCreationStamp(indexName);
      return stamp == indexCreationStamp ? FileIndexingStateWithExplanation.upToDate() : FileIndexingStateWithExplanation.outdated(
        () -> "stamp(" + stamp + ") != indexCreationStamp(" + indexCreationStamp + ")");
    }
    catch (RuntimeException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        // in case of IO exceptions, consider the file unindexed
        return FileIndexingStateWithExplanation.outdated("RuntimeException caused by IOException");
      }
      throw e;
    }
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

  private static final int INDEXING_STAMP_CACHE_CAPACITY = getIntProperty("index.timestamp.cache.size", 100);

  //MAYBE RC: do we still need in-memory cache (fileId->Timestamps)? With new fast-attributes + fast enumerator
  //          access may be fast enough even without caching -- or, at least, it may be worth caching enumerator
  //          records (which are 100-1000 records at max) _only_
  private static final ConcurrentIntObjectMap<Timestamps> timestampsCache = createConcurrentIntObjectMap();
  private static final BlockingQueue<Integer> finishedFiles = new ArrayBlockingQueue<>(INDEXING_STAMP_CACHE_CAPACITY);

  /**
   * The lock protects reading/modifying the {@link Timestamps} state for fileId.
   * It doesn't protect {@link #timestampsCache} -- it is a concurrent map itself, doesn't need protection.
   */
  private static final StripedLock timestampsPerFileLock = new StripedLock();

  private static final AutoRefreshingOnVfsCloseRef<IndexingStampStorage> storage =
    new AutoRefreshingOnVfsCloseRef<>(IndexingStamp::createStorage);

  // Read lock is used to flush caches. Write lock is to wait until all threads have finished flushing.
  // This is kind of abuse of RW lock. The goal is to allow concurrent execution of flushCache(int finishedFile) from different threads.
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
    timestampsCache.clear();
  }

  public static long getIndexStamp(int fileId, ID<?, ?> indexName) {
    return timestampsPerFileLock.withReadLock(fileId, () -> {
      Timestamps stamp = createOrGetTimeStamp(fileId);
      return stamp.get(indexName);
    });
  }

  @TestOnly
  public static void dropIndexingTimeStamps(int fileId) throws IOException {
    timestampsCache.remove(fileId);
    storage.invoke().writeTimestamps(fileId, TimestampsImmutable.EMPTY);
  }

  private static @NotNull Timestamps createOrGetTimeStamp(int id) {
    return getTimestamp(id, true);
  }

  @Contract("_, true->!null")
  private static Timestamps getTimestamp(int id, boolean createIfNoneSaved) {
    assert id > 0;
    Timestamps timestamps = timestampsCache.get(id);
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
    timestampsCache.cacheOrGet(id, timestamps);
    return timestamps;
  }

  @TestOnly
  public static boolean hasIndexingTimeStamp(int fileId) {
    Timestamps timestamp = getTimestamp(fileId, false);
    return timestamp != null && timestamp.hasIndexingTimeStamp();
  }

  public static void update(int fileId, @NotNull ID<?, ?> indexName, final long indexCreationStamp) {
    assert fileId > 0;
    timestampsPerFileLock.withWriteLock(fileId, () -> {
      Timestamps stamp = createOrGetTimeStamp(fileId);
      stamp.set(indexName, indexCreationStamp);
      return null;
    });
  }

  /**
   * Non-trivial means "up to date" or "outdated".
   * <p>
   * "unindexed" is not included.
   */
  public static @NotNull List<ID<?, ?>> getNontrivialFileIndexedStates(int fileId) {
    return timestampsPerFileLock.withReadLock(fileId, () -> {
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

  /** Persist cached data {@link Timestamps} for finishedFile */
  public static void flushCache(int finishedFile) {
    boolean exit = timestampsPerFileLock.withReadLock(finishedFile, () -> {
      Timestamps timestamps = timestampsCache.get(finishedFile);
      if (timestamps == null) return true;
      if (!timestamps.isDirty()) {
        timestampsCache.remove(finishedFile);
        return true;
      }
      return false;
    });
    if (exit) return;

    while (!finishedFiles.offer(finishedFile)) {
      doFlush();
    }
  }

  @TestOnly
  public static int @NotNull [] dumpCachedUnfinishedFiles() {
    return timestampsPerFileLock.withAllLocksWriteLocked(() -> {
      int[] cachedKeys = timestampsCache
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
        Set<Integer> finishedIds = new HashSet<>(finishedFiles);
        cachedIds.removeAll(finishedIds);
        return cachedIds.toIntArray();
      }
    });
  }

  private static void doFlush() {
    flushLock.readLock().lock();
    try {
      List<Integer> files = new ArrayList<>(finishedFiles.size());
      finishedFiles.drainTo(files);

      if (!files.isEmpty()) {
        for (Integer fileId : files) {
          IOException exception = timestampsPerFileLock.withWriteLock(fileId, () -> {
            try {
              Timestamps timestamp = timestampsCache.remove(fileId);
              if (timestamp == null) return null;

              if (timestamp.isDirty() /*&& file.isValid()*/) {
                //RC: writeTimestamps() _could_ be re-implemented via raw attribute bytebuffer access, but now I don't see the benefits
                //    for now: doFlush() is mostly outside the critical path, while implementing timestamps.writeToBuffer(buffer) is
                //    complicated with all those variable-sized numbers used.
                storage.invoke().writeTimestamps(fileId, timestamp.toImmutable());
              }
              return null;
            }
            catch (IOException e) {
              return e;
            }
          });
          if (exception != null) {
            throw new UncheckedIOException(exception);
          }
        }
      }
    }
    finally {
      flushLock.readLock().unlock();
    }
  }

  static boolean isDirty() {
    return !finishedFiles.isEmpty();
  }

  static void close() {
    flushCaches();
    storage.close();
  }
}