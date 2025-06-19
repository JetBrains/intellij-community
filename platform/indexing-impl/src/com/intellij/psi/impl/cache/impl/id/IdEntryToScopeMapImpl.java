// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.openapi.util.ThreadLocalCachedIntArray;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.RepresentableAsByteArraySequence;
import com.intellij.util.io.UnsyncByteArrayOutputStream;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.IntBinaryOperator;

/**
 * Implementation of {@link IdEntryToScopeMap}, uses {@link Int2IntMap} under the hood
 */
@ApiStatus.Internal
public class IdEntryToScopeMapImpl extends AbstractMap<IdIndexEntry, Integer> implements IdEntryToScopeMap,
                                                                                         RepresentableAsByteArraySequence {

  //TODO RC: since occurence mask is really a byte, Int2ByteOpenHashMap will be even more optimal
  private final @NotNull Int2IntOpenHashMapWithFastMergeInt idHashToScopeMask;

  public IdEntryToScopeMapImpl() {
    this(new Int2IntOpenHashMapWithFastMergeInt());
  }

  public IdEntryToScopeMapImpl(int initialCapacity) {
    this(new Int2IntOpenHashMapWithFastMergeInt(initialCapacity));
  }

  public IdEntryToScopeMapImpl(@NotNull Map<IdIndexEntry, Integer> toCopy) {
    this(compactCopyOf(toCopy));
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

    if (serializedData != null) {
      serializedData = null;
    }
  }

  /**
   * Cached serialized form of the map -- allows calculating the serialized form early on, and avoid
   * doing it, during index update, under the lock
   */
  private transient ByteArraySequence serializedData = null;

  ByteArraySequence ensureSerializedDataCached() {
    if (serializedData == null) {
      int estimatedBufferSize = size() * 2 + 2;//assumed diff-compression: 2bytes per ID hash
      UnsyncByteArrayOutputStream stream = new UnsyncByteArrayOutputStream(estimatedBufferSize);
      try (DataOutputStream dos = new DataOutputStream(stream)) {
        writeTo(this, dos);
      }
      catch (IOException e) {
        //ideally, byte[]-based output stream should never throw IOException -- but the serialization code
        // _could_ throw it, so we wrap it in UncheckedIOException
        throw new UncheckedIOException(e);
      }
      serializedData = stream.toByteArraySequence();
    }

    return serializedData;
  }

  @Override
  public @NotNull ByteArraySequence asByteArraySequence() {
    return ensureSerializedDataCached();
  }

  public void writeTo(@NotNull DataOutput out) throws IOException {
    ensureSerializedDataCached();

    out.write(
      serializedData.getInternalBuffer(),
      serializedData.getOffset(),
      serializedData.getLength()
    );
  }

  private static final ThreadLocalCachedIntArray intsArrayPool = new ThreadLocalCachedIntArray();

  private static void writeTo(@NotNull IdEntryToScopeMap idToScopeMap,
                              @NotNull DataOutput out) throws IOException {
    int size = idToScopeMap.size();
    DataInputOutputUtil.writeINT(out, size);

    if (size == 0) {
      return;
    }

    //Store Map[IdHash -> ScopeMask] as inverted Map[ScopeMask -> List[IdHashes]] because sorted List[IdHashes] could
    // be stored with diff-compression, which is significant space reduction especially with long lists
    // (resulting binary format is fully compatible with that default InputMapExternalizer produces)

    Int2ObjectMap<IntSet> scopeMaskToHashes = new Int2ObjectOpenHashMap<>(8);
    idToScopeMap.forEach((idHash, scopeMask) -> {
      IntSet idHashes = scopeMaskToHashes.computeIfAbsent(scopeMask, __ -> new IntOpenHashSet());
      idHashes.add(idHash);
      return true;
    });

    //MAYBE RC: use IntArrayList() instead of IntOpenHashSet()? -- we sort the resulting set anyway, so we could
    //          very well skip duplicates after the sort, in O(N)
    for (int scopeMask : scopeMaskToHashes.keySet()) {
      out.writeByte(scopeMask & UsageSearchContext.ANY);

      IntSet idHashes = scopeMaskToHashes.get(scopeMask);
      int hashesCount = idHashes.size();
      if (hashesCount == 0) {
        throw new IllegalStateException("hashesCount(scope: " + scopeMask + ")(=" + hashesCount + ") must be > 0");
      }

      int[] buffer = intsArrayPool.getBuffer(hashesCount);
      idHashes.toArray(buffer);
      save(out, buffer, hashesCount);
    }
  }

  /** BEWARE: idHashes is _modified_ (sorted) during the method call */
  private static void save(DataOutput out,
                           int[] idHashes,
                           int size) throws IOException {
    Arrays.sort(idHashes, 0, size);
    DataInputOutputUtil.writeDiffCompressed(out, idHashes, size);
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
          if (newValue == oldValue) {
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
          if (newValue == oldValue) {
            return oldValue;
          }
          this.value[pos] = newValue;
          return newValue;
        }
      }
    }

  }

  private static Int2IntOpenHashMapWithFastMergeInt compactCopyOf(@NotNull Map<IdIndexEntry, Integer> toCopy) {
    Int2IntOpenHashMapWithFastMergeInt copy = new Int2IntOpenHashMapWithFastMergeInt(toCopy.size());
    for (Map.Entry<IdIndexEntry, Integer> entry : toCopy.entrySet()) {
      copy.put(
        entry.getKey().getWordHashCode(),
        entry.getValue().intValue()
      );
    }
    return copy;
  }
}
