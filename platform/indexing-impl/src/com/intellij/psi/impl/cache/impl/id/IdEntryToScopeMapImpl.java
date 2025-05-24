// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.id;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.IntBinaryOperator;

/**
 * Implementation of {@link IdEntryToScopeMap}, uses {@link Int2IntMap} under the hood
 */
@ApiStatus.Internal
public class IdEntryToScopeMapImpl extends AbstractMap<IdIndexEntry, Integer> implements IdEntryToScopeMap {

  //TODO RC: since occurence mask is really a byte, Int2ByteOpenHashMap will be even more optimal
  private final @NotNull Int2IntOpenHashMapWithFastMergeInt idHashToScopeMask;

  public IdEntryToScopeMapImpl() {
    this(new Int2IntOpenHashMapWithFastMergeInt());
  }

  public IdEntryToScopeMapImpl(int initialCapacity) {
    this(new Int2IntOpenHashMapWithFastMergeInt(initialCapacity));
  }

  private IdEntryToScopeMapImpl(@NotNull Int2IntOpenHashMapWithFastMergeInt hashToScopeMask) {
    idHashToScopeMask = hashToScopeMask;
  }

  @Override
  public int size() {
    return idHashToScopeMask.size();
  }

  @Override
  public boolean containsKey(Object key) {
    if (key instanceof IdIndexEntry entry) {
      return idHashToScopeMask.containsKey(entry.getWordHashCode());
    }
    return false;
  }

  @Override
  public Integer get(Object key) {
    if (key instanceof IdIndexEntry entry) {
      return idHashToScopeMask.get(entry.getWordHashCode());
    }
    return null;
  }

  @Override
  public @NotNull Set<IdIndexEntry> keySet() {
    return new AbstractSet<>() {
      @Override
      public boolean contains(Object o) {
        return containsKey(o);
      }

      @Override
      public @NotNull Iterator<IdIndexEntry> iterator() {
        return new Iterator<>() {
          final IntIterator iterator = idHashToScopeMask.keySet().iterator();

          @Override
          public boolean hasNext() {
            return iterator.hasNext();
          }

          @Override
          public IdIndexEntry next() {
            return new IdIndexEntry(iterator.nextInt());
          }
        };
      }

      @Override
      public int size() {
        return idHashToScopeMask.size();
      }
    };
  }

  @Override
  public @NotNull Collection<Integer> values() {
    return idHashToScopeMask.values();
  }

  @Override
  public @NotNull Set<Entry<IdIndexEntry, Integer>> entrySet() {
    return new AbstractSet<>() {
      @Override
      public @NotNull Iterator<Entry<IdIndexEntry, Integer>> iterator() {
        return new Iterator<>() {
          final ObjectIterator<Int2IntMap.Entry> iterator = idHashToScopeMask.int2IntEntrySet().iterator();

          @Override
          public boolean hasNext() {
            return iterator.hasNext();
          }

          @Override
          public Entry<IdIndexEntry, Integer> next() {
            Int2IntMap.Entry entry = iterator.next();
            return Map.entry(new IdIndexEntry(entry.getIntKey()), entry.getIntValue());
          }
        };
      }

      @Override
      public int size() {
        return idHashToScopeMask.size();
      }
    };
  }

  @Override
  public void forEach(@NotNull BiConsumer<? super IdIndexEntry, ? super Integer> consumer) {
    forEach((hash, value) -> {
      consumer.accept(new IdIndexEntry(hash), value);
      return true;
    });
  }

  @Override
  public void forEach(@NotNull BiIntConsumer consumer) {
    ObjectIterator<Int2IntMap.Entry> iterator = idHashToScopeMask.int2IntEntrySet().fastIterator();
    while (iterator.hasNext()) {
      Int2IntMap.Entry entry = iterator.next();
      int idHash = entry.getIntKey();
      int scopeMask = entry.getIntValue();
      if (!consumer.consume(idHash, scopeMask)) {
        return;
      }
    }
  }

  public void updateMask(int hash,
                         int occurrenceMask) {
    idHashToScopeMask.mergeInt(hash, occurrenceMask, (prev, cur) -> prev | cur);
  }

  /**
   * We use {@link Int2IntMap#mergeInt(int, int, IntBinaryOperator)} a lot in this class, but its implementation
   * in {@link Int2IntMap} is very generic, hence not very efficient -- so we provide a more efficient implementation
   * here.
   * Improvement is not very significant, but still.
   */
  public static class Int2IntOpenHashMapWithFastMergeInt extends Int2IntOpenHashMap {
    public Int2IntOpenHashMapWithFastMergeInt() {
    }

    public Int2IntOpenHashMapWithFastMergeInt(int expected) {
      super(expected);
    }


    @Override
    public int mergeInt(int key,
                        int value,
                        @NotNull IntBinaryOperator remappingFunction) {
      if (key == 0) {//0 is a special value, which means 'free slot', so key=0 needs special processing
        if (containsNullKey) {
          int oldValue = this.value[n];
          int newValue = remappingFunction.applyAsInt(oldValue, value);
          if(newValue == oldValue){
            return oldValue;
          }
          this.value[n] = newValue;
          return newValue;
        }
        else {
          put(key, value);
          return value;
        }
      }

      int[] keys = this.key;
      for (int pos = HashCommon.mix(key) & mask; ; pos = (pos + 1) & mask) {
        int currKey = keys[pos];
        if (currKey == 0) {// key is not found
          put(key, value);
          return value;
        }
        else if (key == currKey) {
          int oldValue = this.value[pos];
          int newValue = remappingFunction.applyAsInt(oldValue, value);
          if(newValue == oldValue){
            return oldValue;
          }
          this.value[pos] = newValue;
          return newValue;
        }
      }
    }
  }
}
