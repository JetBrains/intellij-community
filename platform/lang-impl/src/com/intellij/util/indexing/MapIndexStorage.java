/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Eugene Zhuravlev
*         Date: Dec 20, 2007
*/
public final class MapIndexStorage<Key, Value> implements IndexStorage<Key, Value>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.MapIndexStorage");
  private PersistentMap<Key, ValueContainer<Value>> myMap;
  private SLRUCache<Key, ChangeTrackingValueContainer<Value>> myCache;
  private final File myStorageFile;
  private final KeyDescriptor<Key> myKeyDescriptor;
  private final int myCacheSize;

  private final Lock l = new ReentrantLock();
  private final DataExternalizer<Value> myDataExternalizer;

  public MapIndexStorage(File storageFile, final KeyDescriptor<Key> keyDescriptor,
                         final DataExternalizer<Value> valueExternalizer,
                         final int cacheSize) throws IOException {

    myStorageFile = storageFile;
    myKeyDescriptor = keyDescriptor;
    myCacheSize = cacheSize;
    myDataExternalizer = valueExternalizer;
    initMapAndCache();
  }

  private void initMapAndCache() throws IOException {
    final ValueContainerMap<Key, Value> map = new ValueContainerMap<Key, Value>(myStorageFile, myKeyDescriptor, myDataExternalizer);
    myCache = new SLRUCache<Key, ChangeTrackingValueContainer<Value>>(myCacheSize, (int)(Math.ceil(myCacheSize * 0.25)) /* 25% from the main cache size*/) {
      @Override
      @NotNull
      public ChangeTrackingValueContainer<Value> createValue(final Key key) {
        return new ChangeTrackingValueContainer<Value>(new ChangeTrackingValueContainer.Initializer<Value>() {
          @Override
          public Object getLock() {
            return map.getDataAccessLock();
          }

          @Override
          public ValueContainer<Value> compute() {
            ValueContainer<Value> value;
            try {
              value = map.get(key);
              if (value == null) {
                value = new ValueContainerImpl<Value>();
              }
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
            return value;
          }
        });
      }

      @Override
      protected void onDropFromCache(final Key key, final ChangeTrackingValueContainer<Value> valueContainer) {
        if (valueContainer.isDirty()) {
          try {
            map.put(key, valueContainer);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      }
    };

    myMap = map;
  }

  @Override
  public void flush() {
    l.lock();
    try {
      if (!myMap.isClosed() && myMap.isDirty()) {
        myCache.clear();
        myMap.force();
      }
    }
    finally {
      l.unlock();
    }
  }

  @Override
  public void close() throws StorageException {
    try {
      flush();
      myMap.close();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw new StorageException(cause);
      }
      if (cause instanceof StorageException) {
        throw (StorageException)cause;
      }
      throw e;
    }
  }

  @Override
  public void clear() throws StorageException{
    try {
      myMap.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
    try {
      FileUtil.delete(myStorageFile);
      initMapAndCache();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw new StorageException(cause);
      }
      if (cause instanceof StorageException) {
        throw (StorageException)cause;
      }
      throw e;
    }
  }

  @Override
  public boolean processKeys(final Processor<Key> processor) throws StorageException {
    l.lock();
    try {
      myCache.clear(); // this will ensure that all new keys are made into the map
      return myMap.processKeys(processor);
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw new StorageException(cause);
      }
      if (cause instanceof StorageException) {
        throw (StorageException)cause;
      }
      throw e;
    }
    finally {
      l.unlock();
    }
  }

  @Override
  public Collection<Key> getKeys() throws StorageException {
    List<Key> keys = new ArrayList<Key>();
    processKeys(new CommonProcessors.CollectProcessor<Key>(keys));
    return keys;
  }

  @Override
  @NotNull
  public ChangeTrackingValueContainer<Value> read(final Key key) throws StorageException {
    l.lock();
    try {
      return myCache.get(key);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw new StorageException(cause);
      }
      if (cause instanceof StorageException) {
        throw (StorageException)cause;
      }
      throw e;
    }
    finally {
      l.unlock();
    }
  }

  @Override
  public void addValue(final Key key, final int inputId, final Value value) throws StorageException {
    try {
      myMap.markDirty();
      read(key).addValue(inputId, value);
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void removeValue(final Key key, final int inputId, final Value value) throws StorageException {
    try {
      myMap.markDirty();
      read(key).removeValue(inputId, value);
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void removeAllValues(Key key, int inputId) throws StorageException {
    try {
      myMap.markDirty();
      // important: assuming the key exists in the index
      read(key).removeAssociatedValue(inputId);
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }

}
