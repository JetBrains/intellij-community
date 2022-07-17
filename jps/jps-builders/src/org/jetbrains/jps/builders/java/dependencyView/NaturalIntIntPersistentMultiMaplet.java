// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.util.containers.SLRUCache;
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
  private static final int CACHE_SIZE = 128;
  private final PersistentHashMap<Integer, IntSet> myMap;
  private final SLRUCache<Integer, IntSet> myCache;
  private static final DataExternalizer<IntSet> EXTERNALIZER = new DataExternalizer<IntSet>() {
    @Override
    public void save(@NotNull final DataOutput out, final IntSet value) throws IOException {
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
    public IntSet read(@NotNull final DataInput in) throws IOException {
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

  NaturalIntIntPersistentMultiMaplet(final File file, final KeyDescriptor<Integer> keyExternalizer) throws IOException {
    final Boolean prevValue = PersistentHashMapValueStorage.CreationTimeOptions.COMPACT_CHUNKS_WITH_VALUE_DESERIALIZATION.get();
    try {
      PersistentHashMapValueStorage.CreationTimeOptions.COMPACT_CHUNKS_WITH_VALUE_DESERIALIZATION.set(Boolean.TRUE);
      myMap = new PersistentHashMap<>(file, keyExternalizer, EXTERNALIZER);
    }
    finally {
      PersistentHashMapValueStorage.CreationTimeOptions.COMPACT_CHUNKS_WITH_VALUE_DESERIALIZATION.set(prevValue);
    }
    myCache = new SLRUCache<Integer, IntSet>(CACHE_SIZE, 2 * CACHE_SIZE) {
      @NotNull
      @Override
      public IntSet createValue(Integer key) {
        try {
          final IntSet collection = myMap.get(key);
          if (collection == null) {
            return NULL_COLLECTION;
          }
          if (collection.isEmpty()) {
            myMap.remove(key);
            return NULL_COLLECTION;
          }
          return collection;
        }
        catch (IOException e) {
          throw new BuildDataCorruptedException(e);
        }
      }
    };
  }

  @Override
  public boolean containsKey(final int key) {
    try {
      return myMap.containsMapping(key);
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public IntSet get(final int key) {
    final IntSet collection = myCache.get(key);
    return collection == NULL_COLLECTION? null : collection;
  }

  @Override
  public void replace(int key, IntSet value) {
    try {
      myCache.remove(key);
      if (value == null || value.isEmpty()) {
        myMap.remove(key);
      }
      else {
        myMap.put(key, value);
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
        myCache.remove(key);
        myMap.appendData(key, getAddAppender(values));
      }
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void put(final int key, final int value) {
    try {
      myCache.remove(key);
      myMap.appendData(key, getAddAppender(value));
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void removeAll(int key, IntSet values) {
    try {
      if (!values.isEmpty()) {
        myCache.remove(key);
        myMap.appendData(key, getRemoveAppender(values));
      }
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void removeFrom(final int key, final int value) {
    try {
      final IntSet collection = myCache.get(key);
      if (collection != NULL_COLLECTION) {
        if (collection.remove(value)) {
          myCache.remove(key);
          if (collection.isEmpty()) {
            myMap.remove(key);
          }
          else {
            myMap.appendData(key, getRemoveAppender(value));
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
      myCache.remove(key);
      myMap.remove(key);
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
      myCache.clear();
      myMap.close();
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void flush(boolean memoryCachesOnly) {
    if (memoryCachesOnly) {
      if (myMap.isDirty()) {
        myMap.dropMemoryCaches();
      }
    }
    else {
      myMap.force();
    }
  }

  @Override
  void forEachEntry(ObjIntConsumer<? super IntSet> proc) {
    try {
      myMap.processKeysWithExistingMapping(key -> {
        try {
          proc.accept(myMap.get(key), key);
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

  @NotNull
  private static AppendablePersistentMap.ValueDataAppender getAddAppender(int value) {
    return out -> DataInputOutputUtil.writeINT(out, value);
  }

  @NotNull
  private static AppendablePersistentMap.ValueDataAppender getRemoveAppender(int value) {
    return out -> DataInputOutputUtil.writeINT(out, -value);
  }

  @NotNull
  private static AppendablePersistentMap.ValueDataAppender getAddAppender(final IntSet set) {
    return getAppender(set, value -> value);
  }

  @NotNull
  private static AppendablePersistentMap.ValueDataAppender getRemoveAppender(final IntSet set) {
    return getAppender(set, value -> -value);
  }

  @NotNull
  private static AppendablePersistentMap.ValueDataAppender getAppender(final IntSet set, final IntUnaryOperator converter) {
    return new AppendablePersistentMap.ValueDataAppender() {
      @Override
      public void append(@NotNull final DataOutput out) throws IOException {
        IntIterator iterator = set.iterator();
        while (iterator.hasNext()) {
          int v = iterator.nextInt();
          DataInputOutputUtil.writeINT(out, converter.applyAsInt(v));
        }
      }
    };
  }
}
