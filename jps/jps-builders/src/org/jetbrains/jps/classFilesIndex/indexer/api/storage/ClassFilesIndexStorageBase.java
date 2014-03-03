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
package org.jetbrains.jps.classFilesIndex.indexer.api.storage;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectProcedure;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Dmitry Batkovich
 */
public class ClassFilesIndexStorageBase<K, V> {
  private static final String INDEX_FILE_NAME = "index";
  private static final int INITIAL_INDEX_SIZE = 16 * 1024;
  private static final int CACHE_QUEUES_SIZE = 16 * 1024;

  private final File myIndexFile;
  private final KeyDescriptor<K> myKeyDescriptor;
  private final DataExternalizer<V> myValueExternalizer;
  private PersistentHashMap<K, CompiledDataValueContainer<V>> myMap;

  protected final Lock myWriteLock = new ReentrantLock();
  protected SLRUCache<K, CompiledDataValueContainer<V>> myCache;

  public ClassFilesIndexStorageBase(final File indexDir, final KeyDescriptor<K> keyDescriptor, final DataExternalizer<V> valueExternalizer)
    throws IOException {
    myIndexFile = getIndexFile(indexDir);
    myKeyDescriptor = keyDescriptor;
    myValueExternalizer = valueExternalizer;
    initialize();
  }

  private void initialize() throws IOException {
    myMap = new PersistentHashMap<K, CompiledDataValueContainer<V>>(myIndexFile, myKeyDescriptor,
                                                                    createValueContainerExternalizer(myValueExternalizer),
                                                                    INITIAL_INDEX_SIZE);
    myCache = new SLRUCache<K, CompiledDataValueContainer<V>>(CACHE_QUEUES_SIZE, CACHE_QUEUES_SIZE) {
      @NotNull
      @Override
      public CompiledDataValueContainer<V> createValue(final K key) {
        try {
          final CompiledDataValueContainer<V> valueContainer = myMap.get(key);
          if (valueContainer != null) {
            return valueContainer;
          }
        }
        catch (final IOException e) {
          throw new RuntimeException(e);
        }
        return new CompiledDataValueContainer<V>();
      }

      @Override
      protected void onDropFromCache(final K key, final CompiledDataValueContainer<V> value) {
        try {
          myMap.put(key, value);
        }
        catch (final IOException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  public void delete() throws IOException {
    try {
      myWriteLock.lock();
      doDelete();
    }
    finally {
      myWriteLock.unlock();
    }
  }

  private void doDelete() throws IOException {
    close();
    PersistentHashMap.deleteFilesStartingWith(myIndexFile);
  }

  public void clear() throws IOException {
    try {
      myWriteLock.lock();
      doDelete();
      initialize();
    }
    finally {
      myWriteLock.unlock();
    }
  }

  public void flush() {
    try {
      myWriteLock.lock();
      myCache.clear();
    }
    finally {
      myWriteLock.unlock();
    }
    myMap.force();
  }

  public void close() throws IOException {
    flush();
    myMap.close();
  }

  public static class CompiledDataValueContainer<V> {
    private final TIntObjectHashMap<V> myUnderlying;

    private CompiledDataValueContainer(final TIntObjectHashMap<V> map) {
      myUnderlying = map;
    }

    private CompiledDataValueContainer() {
      this(new TIntObjectHashMap<V>());
    }

    public void putValue(final Integer inputId, final V value) {
      myUnderlying.put(inputId, value);
    }

    public Collection<V> getValues() {
      return ContainerUtil.list((V[])myUnderlying.getValues());
    }

  }

  public static File getIndexFile(final File indexDir) {
    return new File(indexDir, INDEX_FILE_NAME);
  }

  public static File getIndexDir(final String indexName, final File projectSystemBuildDirectory) {
    return new File(projectSystemBuildDirectory, "compiler.output.data.indices/" + indexName);
  }

  private static <V> DataExternalizer<CompiledDataValueContainer<V>> createValueContainerExternalizer(final DataExternalizer<V> valueExternalizer) {
    return new DataExternalizer<CompiledDataValueContainer<V>>() {
      @Override
      public void save(@NotNull final DataOutput out, final CompiledDataValueContainer<V> value) throws IOException {
        final TIntObjectHashMap<V> underlying = value.myUnderlying;
        out.writeInt(underlying.size());
        final IOException[] ioException = {null};
        underlying.forEachEntry(new TIntObjectProcedure<V>() {
          @Override
          public boolean execute(final int k, final V v) {
            try {
              EnumeratorIntegerDescriptor.INSTANCE.save(out, k);
              valueExternalizer.save(out, v);
              return true;
            }
            catch (final IOException e) {
              ioException[0] = e;
              return false;
            }
          }
        });
        if (ioException[0] != null) {
          throw ioException[0];
        }
      }

      @Override
      public CompiledDataValueContainer<V> read(@NotNull final DataInput in) throws IOException {
        final TIntObjectHashMap<V> map = new TIntObjectHashMap<V>();
        final int size = in.readInt();
        for (int i = 0; i < size; i++) {
          map.put(EnumeratorIntegerDescriptor.INSTANCE.read(in), valueExternalizer.read(in));
        }
        return new CompiledDataValueContainer<V>(map);
      }
    };
  }
}
