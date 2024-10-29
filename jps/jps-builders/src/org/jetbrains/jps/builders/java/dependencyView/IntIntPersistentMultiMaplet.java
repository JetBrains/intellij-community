// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.intellij.util.io.*;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import java.io.*;
import java.util.function.ObjIntConsumer;

final class IntIntPersistentMultiMaplet extends IntIntMultiMaplet {
  private static final IntSet NULL_COLLECTION = IntSets.emptySet();
  private static final int CACHE_SIZE = 256;
  private final PersistentHashMap<Integer, IntSet> map;
  private final LoadingCache<Integer, IntSet> cache;

  IntIntPersistentMultiMaplet(@NotNull File file, final KeyDescriptor<Integer> keyExternalizer) throws IOException {
    map = new PersistentHashMap<>(file.toPath(), keyExternalizer, new IntSetExternalizer());
    cache = Caffeine.newBuilder().maximumSize(CACHE_SIZE).build(key -> {
      try {
        IntSet collection = map.get(key);
        return collection == null ? NULL_COLLECTION : collection;
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
  public void put(final int key, final IntSet value) {
    try {
      cache.invalidate(key);
      map.appendData(key, new AppendablePersistentMap.ValueDataAppender() {
        @Override
        public void append(final @NotNull DataOutput out) throws IOException {
          IntIterator iterator = value.iterator();
          while (iterator.hasNext()) {
            int value1 = iterator.nextInt();
            DataInputOutputUtil.writeINT(out, value1);
          }
        }
      });
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void put(final int key, final int value) {
    try {
      cache.invalidate(key);
      map.appendData(key, new AppendablePersistentMap.ValueDataAppender() {
        @Override
        public void append(final @NotNull DataOutput out) throws IOException {
          DataInputOutputUtil.writeINT(out, value);
        }
      });
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void removeAll(int key, IntSet values) {
    try {
      IntSet collection = cache.get(key);
      if (collection != NULL_COLLECTION) {
        if (collection.removeAll(values)) {
          cache.invalidate(key);
          if (collection.isEmpty()) {
            map.remove(key);
          }
          else {
            map.put(key, collection);
          }
        }
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
            map.put(key, collection);
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
  public void forEachEntry(ObjIntConsumer<? super IntSet> procedure) {
    try {
      map.processKeysWithExistingMapping(key -> {
        try {
          procedure.accept(map.get(key), key);
        }
        catch (IOException e) {
          throw new BuildDataCorruptedException(e);
        }
        return true;
      });
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  private static final class IntSetExternalizer implements DataExternalizer<IntSet> {
    @Override
    public void save(final @NotNull DataOutput out, final IntSet value) throws IOException {
      IntIterator iterator = value.iterator();
      while (iterator.hasNext()) {
        int elem = iterator.nextInt();
        DataInputOutputUtil.writeINT(out, elem);
      }
    }

    @Override
    public IntSet read(final @NotNull DataInput in) throws IOException {
      final IntSet result = new IntOpenHashSet();
      final DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        result.add(DataInputOutputUtil.readINT(in));
      }
      return result;
    }
  }
}
