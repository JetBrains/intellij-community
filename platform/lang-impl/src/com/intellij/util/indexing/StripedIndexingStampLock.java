// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.util.SmartList;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

class StripedIndexingStampLock {
  /**
   * This value is used when there is no hash for provided id. 'Real' hashes are never equal to it.
   */
  static final long NON_EXISTENT_HASH = 0L;
  private static final int LOCK_SIZE = 16;
  private final ReadWriteLock[] myLocks = new ReadWriteLock[LOCK_SIZE];
  private final Int2LongMap[] myHashes = new Int2LongMap[LOCK_SIZE];
  private final AtomicLong myCurrentHash = new AtomicLong(NON_EXISTENT_HASH + 1);

  StripedIndexingStampLock() {
    for (int i = 0; i < myLocks.length; ++i) {
      myLocks[i] = new ReentrantReadWriteLock();
      myHashes[i] = new Int2LongOpenHashMap();
    }
  }

  /**
   * @return NON_EXISTENT_HASH if there was no hash for this id,
   * and hash (>0) if there was one
   */
  long releaseHash(int id) {
    Lock lock = getLock(id).writeLock();
    lock.lock();
    try {
      return getHashes(id).remove(id);
    }
    finally {
      lock.unlock();
    }
  }

  long getHash(int id) {
    Lock lock = getLock(id).writeLock();
    lock.lock();
    try {
      Int2LongMap hashes = getHashes(id);
      long hash = hashes.get(id);
      if (hash == NON_EXISTENT_HASH) {
        hash = myCurrentHash.getAndIncrement();
        hashes.put(id, hash);
      }
      return hash;
    }
    finally {
      lock.unlock();
    }
  }

  private ReadWriteLock getLock(int fileId) {
    return myLocks[getIndex(fileId)];
  }

  private int getIndex(int fileId) {
    if (fileId < 0) fileId = -fileId;
    return (fileId & 0xFF) % myLocks.length;
  }

  private Int2LongMap getHashes(int fileId) {
    return myHashes[getIndex(fileId)];
  }

  void clear() {
    forEachStripe(false, Int2LongMap::clear);
  }

  int[] dumpIds() {
    IntList result = new IntArrayList();
    forEachStripe(true, (Int2LongMap map) -> {
      result.addAll(map.keySet());
    });
    return result.toIntArray();
  }

  private void forEachStripe(boolean readLock, Consumer<Int2LongMap> consumer) {
    List<Exception> exceptions = new SmartList<>();
    for (int i = 0; i < myLocks.length; i++) {
      Lock lock = null;
      try {
        ReadWriteLock readWriteLock = getLock(i);
        lock = readLock ? readWriteLock.readLock() : readWriteLock.writeLock();
        lock.lock();
        consumer.accept(getHashes(i));
      }
      catch (Exception e) {
        exceptions.add(e);
      }
      finally {
        if (lock != null) {
          try {
            lock.unlock();
          }
          catch (Exception e) {
            exceptions.add(e);
          }
        }
      }
    }
    if (!exceptions.isEmpty()) {
      IllegalStateException exception = new IllegalStateException("Exceptions while clearing");
      for (Exception suppressed : exceptions) {
        exception.addSuppressed(suppressed);
      }
      throw exception;
    }
  }
}
