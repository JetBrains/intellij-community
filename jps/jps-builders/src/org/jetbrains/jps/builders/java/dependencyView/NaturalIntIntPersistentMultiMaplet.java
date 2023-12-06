// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.util.io.*;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import java.io.*;
import java.util.function.IntUnaryOperator;
import java.util.function.ObjIntConsumer;

final class NaturalIntIntPersistentMultiMaplet extends IntIntMultiMaplet {
  private static final IntSet NULL_COLLECTION = new IntOpenHashSet();
  private static final int CACHE_SIZE = 256;
  private static final DataExternalizer<IntSet> EXTERNALIZER = new DataExternalizer<>() {
    @Override
    public void save(final @NotNull DataOutput out, final IntSet value) throws IOException {
      IntIterator iterator = value.iterator();
      while (iterator.hasNext()) {
        int elem = iterator.nextInt();
        if (elem == 0) {
          throw new IOException("Zero values are not supported");
        }
        DataInputOutputUtil.writeINT(out, elem);
      }
    }

    @Override
    public IntSet read(final @NotNull DataInput in) throws IOException {
      final IntSet result = new IntOpenHashSet();
      final DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        final int elem = DataInputOutputUtil.readINT(in);
        if (elem < 0) {
          result.remove(-elem);
        }
        else if (elem > 0) {
          result.add(elem);
        }
      }
      return result;
    }
  };
  private final PersistentHashMap<Integer, IntSet> map;
  private final LoadingCache<Integer, IntSet> cache;

  NaturalIntIntPersistentMultiMaplet(final File file, final KeyDescriptor<Integer> keyExternalizer) throws IOException {
    final Boolean prevValue = PersistentHashMapValueStorage.CreationTimeOptions.COMPACT_CHUNKS_WITH_VALUE_DESERIALIZATION.get();
    try {
      PersistentHashMapValueStorage.CreationTimeOptions.COMPACT_CHUNKS_WITH_VALUE_DESERIALIZATION.set(Boolean.TRUE);
      map = new PersistentHashMap<>(file.toPath(), keyExternalizer, EXTERNALIZER);
    }
    finally {
      PersistentHashMapValueStorage.CreationTimeOptions.COMPACT_CHUNKS_WITH_VALUE_DESERIALIZATION.set(prevValue);
    }
    cache = Caffeine.newBuilder().maximumSize(CACHE_SIZE).build(key -> {
      try {
        IntSet collection = map.get(key);
        if (collection == null) {
          return NULL_COLLECTION;
        }
        if (collection.isEmpty()) {
          map.remove(key);
          return NULL_COLLECTION;
        }
        return collection;
      }
      catch (IOException e) {
        throw new BuildDataCorruptedException(e);
      }
    });
  }

  @Override
  public boolean containsKey(final int key) {
    try {
      return map.containsMapping(key);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public IntSet get(final int key) {
    final IntSet collection = cache.get(key);
    return collection == NULL_COLLECTION ? null : collection;
  }

  @Override
  public void replace(int key, IntSet value) {
    try {
      cache.invalidate(key);
      if (value == null || value.isEmpty()) {
        map.remove(key);
      }
      else {
        map.put(key, value);
      }
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void put(final int key, final IntSet values) {
    try {
      if (!values.isEmpty()) {
        cache.invalidate(key);
        map.appendData(key, getAddAppender(values));
      }
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void put(final int key, final int value) {
    try {
      cache.invalidate(key);
      map.appendData(key, getAddAppender(value));
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void removeAll(int key, IntSet values) {
    try {
      if (!values.isEmpty()) {
        cache.invalidate(key);
        map.appendData(key, getRemoveAppender(values));
      }
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void removeFrom(final int key, final int value) {
    try {
      final IntSet collection = cache.get(key);
      if (collection != NULL_COLLECTION) {
        if (collection.remove(value)) {
          cache.invalidate(key);
          if (collection.isEmpty()) {
            map.remove(key);
          }
          else {
            map.appendData(key, getRemoveAppender(value));
          }
        }
      }
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void remove(final int key) {
    try {
      cache.invalidate(key);
      map.remove(key);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void putAll(IntIntMultiMaplet m) {
    m.forEachEntry((integers, value) -> put(value, integers));
  }

  @Override
  public void replaceAll(IntIntMultiMaplet m) {
    m.forEachEntry((integers, value) -> replace(value, integers));
  }

  @Override
  public void close() {
    try {
      cache.invalidateAll();
      map.close();
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void flush(boolean memoryCachesOnly) {
    if (memoryCachesOnly) {
      if (map.isDirty()) {
        map.dropMemoryCaches();
      }
    }
    else {
      map.force();
    }
  }

  @Override
  void forEachEntry(ObjIntConsumer<? super IntSet> proc) {
    try {
      map.processKeysWithExistingMapping(key -> {
        try {
          proc.accept(map.get(key), key);
          return true;
        }
        catch (IOException e) {
          throw new BuildDataCorruptedException(e);
        }
      });
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  private static @NotNull AppendablePersistentMap.ValueDataAppender getAddAppender(int value) {
    return out -> DataInputOutputUtil.writeINT(out, value);
  }

  private static @NotNull AppendablePersistentMap.ValueDataAppender getRemoveAppender(int value) {
    return out -> DataInputOutputUtil.writeINT(out, -value);
  }

  private static @NotNull AppendablePersistentMap.ValueDataAppender getAddAppender(final IntSet set) {
    return getAppender(set, value -> value);
  }

  private static @NotNull AppendablePersistentMap.ValueDataAppender getRemoveAppender(final IntSet set) {
    return getAppender(set, value -> -value);
  }

  private static @NotNull AppendablePersistentMap.ValueDataAppender getAppender(final IntSet set, final IntUnaryOperator converter) {
    return new AppendablePersistentMap.ValueDataAppender() {
      @Override
      public void append(final @NotNull DataOutput out) throws IOException {
        IntIterator iterator = set.iterator();
        while (iterator.hasNext()) {
          int v = iterator.nextInt();
          DataInputOutputUtil.writeINT(out, converter.applyAsInt(v));
        }
      }
    };
  }
}
