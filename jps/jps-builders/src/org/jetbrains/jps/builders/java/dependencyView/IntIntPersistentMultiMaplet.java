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
import java.util.function.ObjIntConsumer;

public final class IntIntPersistentMultiMaplet extends IntIntMultiMaplet {
  private static final IntSet NULL_COLLECTION = new IntOpenHashSet();
  private static final int CACHE_SIZE = 128;
  private final PersistentHashMap<Integer, IntSet> myMap;
  private final SLRUCache<Integer, IntSet> myCache;

  public IntIntPersistentMultiMaplet(final File file, final KeyDescriptor<Integer> keyExternalizer) throws IOException {
    myMap = new PersistentHashMap<>(file, keyExternalizer, new IntSetExternalizer());
    myCache = new SLRUCache<Integer, IntSet>(CACHE_SIZE, CACHE_SIZE) {
      @NotNull
      @Override
      public IntSet createValue(Integer key) {
        try {
          final IntSet collection = myMap.get(key);
          return collection == null? NULL_COLLECTION : collection;
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
  public void put(final int key, final IntSet value) {
    try {
      myCache.remove(key);
      myMap.appendData(key, new AppendablePersistentMap.ValueDataAppender() {
        @Override
        public void append(@NotNull final DataOutput out) throws IOException {
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
      myCache.remove(key);
      myMap.appendData(key, new AppendablePersistentMap.ValueDataAppender() {
        @Override
        public void append(@NotNull final DataOutput out) throws IOException {
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
      final IntSet collection = myCache.get(key);

      if (collection != NULL_COLLECTION) {
        if (collection.removeAll(values)) {
          myCache.remove(key);
          if (collection.isEmpty()) {
            myMap.remove(key);
          }
          else {
            myMap.put(key, collection);
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
      final IntSet collection = myCache.get(key);
      if (collection != NULL_COLLECTION) {
        if (collection.remove(value)) {
          myCache.remove(key);
          if (collection.isEmpty()) {
            myMap.remove(key);
          }
          else {
            myMap.put(key, collection);
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
  public void forEachEntry(ObjIntConsumer<? super IntSet> procedure) {
    try {
      myMap.processKeysWithExistingMapping(key -> {
        try {
          procedure.accept(myMap.get(key), key);
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
    public void save(@NotNull final DataOutput out, final IntSet value) throws IOException {
      IntIterator iterator = value.iterator();
      while (iterator.hasNext()) {
        int elem = iterator.nextInt();
        DataInputOutputUtil.writeINT(out, elem);
      }
    }

    @Override
    public IntSet read(@NotNull final DataInput in) throws IOException {
      final IntSet result = new IntOpenHashSet();
      final DataInputStream stream = (DataInputStream)in;
      while (stream.available() > 0) {
        result.add(DataInputOutputUtil.readINT(in));
      }
      return result;
    }
  }
}
