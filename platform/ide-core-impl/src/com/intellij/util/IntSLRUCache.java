// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.util.containers.IntObjectLRUMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class IntSLRUCache<T> {
  private static final boolean ourPrintDebugStatistics = false;
  private final IntObjectLRUMap<T> myProtectedQueue;
  private final IntObjectLRUMap<T> myProbationalQueue;
  private int probationalHits;
  private int protectedHits;
  private int misses;

  public IntSLRUCache(int protectedQueueSize, int probationalQueueSize) {
    myProtectedQueue = new IntObjectLRUMap<>(protectedQueueSize);
    myProbationalQueue = new IntObjectLRUMap<>(probationalQueueSize);
  }

  public @NotNull IntObjectLRUMap.MapEntry<T> cacheEntry(int key, T value) {
    IntObjectLRUMap.MapEntry<T> cached = myProtectedQueue.getEntry(key);
    if (cached == null) {
      cached = myProbationalQueue.getEntry(key);
    }
    if (cached != null) {
      return cached;
    }

    IntObjectLRUMap.MapEntry<T> entry = new IntObjectLRUMap.MapEntry<>(key, value);
    myProbationalQueue.putEntry(entry);
    return entry;
  }

  public @Nullable IntObjectLRUMap.MapEntry<T> getCachedEntry(int id) {
    return getCachedEntry(id, true);
  }

  public @Nullable IntObjectLRUMap.MapEntry<T> getCachedEntry(int id, boolean allowMutation) {
    IntObjectLRUMap.MapEntry<T> entry = myProtectedQueue.getEntry(id);
    if (entry != null) {
      protectedHits++;
      return entry;
    }

    entry = myProbationalQueue.getEntry(id);
    if (entry != null) {
      printStatistics(++probationalHits);

      if (allowMutation) {
        myProbationalQueue.removeEntry(entry.key);
        IntObjectLRUMap.MapEntry<T> demoted = myProtectedQueue.putEntry(entry);
        if (demoted != null) {
          myProbationalQueue.putEntry(demoted);
        }
      }
      return entry;
    }

    printStatistics(++misses);

    return null;
  }

  private void printStatistics(int hits) {
    if (ourPrintDebugStatistics && hits % 1000 == 0) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("IntSLRUCache.getCachedEntry time " + System.currentTimeMillis() +
                         ", prot=" + protectedHits + ", prob=" + probationalHits + ", misses=" + misses);
    }
  }

}