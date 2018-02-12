/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.openapi.util.Ref;
import com.intellij.util.Processor;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectProcedure;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;

import java.io.*;

/**
 * @author: db
 */
public class IntIntPersistentMultiMaplet extends IntIntMultiMaplet {
  private static final TIntHashSet NULL_COLLECTION = new TIntHashSet();
  private static final int CACHE_SIZE = 128;
  private final PersistentHashMap<Integer, TIntHashSet> myMap;
  private final SLRUCache<Integer, TIntHashSet> myCache;

  public IntIntPersistentMultiMaplet(final File file, final KeyDescriptor<Integer> keyExternalizer) throws IOException {
    myMap = new PersistentHashMap<>(file, keyExternalizer, new IntSetExternalizer());
    myCache = new SLRUCache<Integer, TIntHashSet>(CACHE_SIZE, CACHE_SIZE) {
      @NotNull
      @Override
      public TIntHashSet createValue(Integer key) {
        try {
          final TIntHashSet collection = myMap.get(key);
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
  public void put(final int key, final TIntHashSet value) {
    try {
      myCache.remove(key);
      myMap.appendData(key, new PersistentHashMap.ValueDataAppender() {
        public void append(final DataOutput out) throws IOException {
          final Ref<IOException> exRef = new Ref<>();
          value.forEach(value1 -> {
            try {
              DataInputOutputUtil.writeINT(out, value1);
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
      myMap.appendData(key, new PersistentHashMap.ValueDataAppender() {
        public void append(final DataOutput out) throws IOException {
          DataInputOutputUtil.writeINT(out, value);
        }
      });
    }
    catch (IOException e) {
      throw new BuildDataCorruptedException(e);
    }
  }

  @Override
  public void removeAll(int key, TIntHashSet values) {
    try {
      final TIntHashSet collection = myCache.get(key);

      if (collection != NULL_COLLECTION) {
        if (collection.removeAll(values.toArray())) {
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
      final TIntHashSet collection = myCache.get(key);
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

  private static class IntSetExternalizer implements DataExternalizer<TIntHashSet> {
    @Override
    public void save(@NotNull final DataOutput out, final TIntHashSet value) throws IOException {
      final Ref<IOException> exRef = new Ref<>(null);
      value.forEach(elem -> {
        try {
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
        result.add(DataInputOutputUtil.readINT(in));
      }
      return result;
    }
  }
}
