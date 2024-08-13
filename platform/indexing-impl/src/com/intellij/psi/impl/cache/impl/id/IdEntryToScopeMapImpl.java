// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.id;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Implementation of {@link IdEntryToScopeMap}, uses {@link Int2IntMap} under the hood
 */
@ApiStatus.Internal
public class IdEntryToScopeMapImpl extends AbstractMap<IdIndexEntry, Integer> implements IdEntryToScopeMap {

  //TODO RC: since occurence mask is really a byte, Int2ByteMap will be more optimal
  private final @NotNull Int2IntMap idHashToScopeMask;

  public IdEntryToScopeMapImpl() {
    this(new Int2IntOpenHashMap());
  }

  public IdEntryToScopeMapImpl(int initialCapacity) {
    this(new Int2IntOpenHashMap(initialCapacity));
  }

  public IdEntryToScopeMapImpl(@NotNull Int2IntMap hashToScopeMask) {
    this.idHashToScopeMask = hashToScopeMask;
  }

  @Override
  public int size() {
    return idHashToScopeMask.size();
  }

  @Override
  public boolean containsKey(Object key) {
    return key instanceof IdIndexEntry entry && idHashToScopeMask.containsKey(entry.getWordHashCode());
  }

  @Override
  public Integer get(Object key) {
    if (key instanceof IdIndexEntry entry) {
      return idHashToScopeMask.get(entry.getWordHashCode());
    }
    return null;
  }

  @NotNull
  @Override
  public Set<IdIndexEntry> keySet() {
    return new AbstractSet<>() {
      @Override
      public boolean contains(Object o) {
        return o instanceof IdIndexEntry entry && idHashToScopeMask.containsKey(entry.getWordHashCode());
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

  @NotNull
  @Override
  public Collection<Integer> values() {
    return idHashToScopeMask.values();
  }

  @NotNull
  @Override
  public Set<Entry<IdIndexEntry, Integer>> entrySet() {
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
  public void forEach(BiConsumer<? super IdIndexEntry, ? super Integer> consumer) {
    idHashToScopeMask.forEach((hash, value) -> consumer.accept(new IdIndexEntry(hash), value));
  }

  @Override
  public void forEach(@NotNull BiIntConsumer consumer) {
    for (Int2IntMap.Entry entry : idHashToScopeMask.int2IntEntrySet()) {
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
}
