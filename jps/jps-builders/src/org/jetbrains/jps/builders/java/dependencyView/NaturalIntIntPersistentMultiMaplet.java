// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.*;
import gnu.trove.TIntFunction;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import java.io.*;

final class NaturalIntIntPersistentMultiMaplet extends IntIntMultiMaplet {
  private static final TIntHashSet NULL_COLLECTION = new TIntHashSet();
  private static final int CACHE_SIZE = 128;
  private final PersistentHashMap<Integer, TIntHashSet> myMap;
  private final SLRUCache<Integer, TIntHashSet> myCache;
  private static final DataExternalizer<TIntHashSet> EXTERNALIZER = new DataExternalizer<TIntHashSet>() {
    @Override
    public void save(@NotNull final DataOutput out, final TIntHashSet value) throws IOException {
      final Ref<IOException> exRef = new Ref<>(null);
      value.forEach(elem -> {
        try {
          if (elem == 0) {
            throw new IOException("Zero values are not supported");
          }
          DataInputOutputUtil.writeINT(out, elem);
        }
        catch (IOException e) {
          exRef.set(e);
          return false;
        }
        return true;
      });
      final IOException exception = exRef.get();
      if (exception != null) {
        throw exception;
      }
    }

    @Override
    public TIntHashSet read(@NotNull final DataInput in) throws IOException {
      final TIntHashSet result = new TIntHashSet();
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
    myCache = new SLRUCache<Integer, TIntHashSet>(CACHE_SIZE, 2 * CACHE_SIZE) {
      @NotNull
      @Override
      public TIntHashSet createValue(Integer key) {
        try {
          final TIntHashSet collection = myMap.get(key);
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
  public TIntHashSet get(final int key) {
    final TIntHashSet collection = myCache.get(key);
    return collection == NULL_COLLECTION? null : collection;
  }

  @Override
  public void replace(int key, TIntHashSet value) {
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
  public void put(final int key, final TIntHashSet values) {
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
  public void removeAll(int key, TIntHashSet values) {
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
      final TIntHashSet collection = myCache.get(key);
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
    m.forEachEntry(new TIntObjectProcedure<TIntHashSet>() {
      @Override
      public boolean execute(int key, TIntHashSet value) {
        put(key, value);
        return true;
      }
    });
  }

  @Override
  public void replaceAll(IntIntMultiMaplet m) {
    m.forEachEntry(new TIntObjectProcedure<TIntHashSet>() {
      @Override
      public boolean execute(int key, TIntHashSet value) {
        replace(key, value);
        return true;
      }
    });
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
  public void forEachEntry(final TIntObjectProcedure<TIntHashSet> procedure) {
    try {
      myMap.processKeysWithExistingMapping(key -> {
        try {
          return procedure.execute(key, myMap.get(key));
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
  private static AppendablePersistentMap.ValueDataAppender getAddAppender(final TIntHashSet set) {
    return getAppender(set, value -> value);
  }

  @NotNull
  private static AppendablePersistentMap.ValueDataAppender getRemoveAppender(final TIntHashSet set) {
    return getAppender(set, value -> -value);
  }

  @NotNull
  private static AppendablePersistentMap.ValueDataAppender getAppender(final TIntHashSet set, final TIntFunction converter) {
    return new AppendablePersistentMap.ValueDataAppender() {
      @Override
      public void append(@NotNull final DataOutput out) throws IOException {
        final Ref<IOException> exRef = new Ref<>();
        set.forEach(v -> {
          try {
            DataInputOutputUtil.writeINT(out, converter.execute(v));
          }
          catch (IOException e) {
            exRef.set(e);
            return false;
          }
          return true;
        });
        final IOException exception = exRef.get();
        if (exception != null) {
          throw exception;
        }
      }
    };
  }

}
