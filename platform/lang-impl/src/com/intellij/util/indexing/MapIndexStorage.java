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
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Eugene Zhuravlev
*         Date: Dec 20, 2007
*/
public final class MapIndexStorage<Key, Value> implements IndexStorage<Key, Value>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.MapIndexStorage");
  private PersistentHashMap<Key, ValueContainer<Value>> myMap;
  private SLRUCache<Key, ChangeTrackingValueContainer<Value>> myCache;
  private final File myStorageFile;
  private final KeyDescriptor<Key> myKeyDescriptor;
  private final ValueContainerExternalizer<Value> myValueContainerExternalizer;
  private final int myCacheSize;

  private final Lock l = new ReentrantLock();

  public MapIndexStorage(File storageFile, final KeyDescriptor<Key> keyDescriptor, final DataExternalizer<Value> valueExternalizer,
                         final int cacheSize) throws IOException {

    myStorageFile = storageFile;
    myKeyDescriptor = keyDescriptor;
    myValueContainerExternalizer = new ValueContainerExternalizer<Value>(valueExternalizer);
    myCacheSize = cacheSize;
    initMapAndCache();
  }

  private void initMapAndCache() throws IOException {
    final PersistentHashMap<Key, ValueContainer<Value>> map =
      new PersistentHashMap<Key, ValueContainer<Value>>(myStorageFile, myKeyDescriptor, myValueContainerExternalizer);
    myCache = new SLRUCache<Key, ChangeTrackingValueContainer<Value>>(myCacheSize, (int)(Math.ceil(myCacheSize * 0.25)) /* 25% from the main cache size*/) {
      @NotNull
      public ChangeTrackingValueContainer<Value> createValue(final Key key) {
        return new ChangeTrackingValueContainer<Value>(new ChangeTrackingValueContainer.Initializer<Value>() {
          public Object getLock() {
            return map;
          }

          public ValueContainer<Value> compute() {
            ValueContainer<Value> value = null;
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

      protected void onDropFromCache(final Key key, final ChangeTrackingValueContainer<Value> valueContainer) {
        if (!valueContainer.isDirty()) {
          return;
        }
        try {
          if (!valueContainer.needsCompacting()) {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            //noinspection IOResourceOpenedButNotSafelyClosed
            final DataOutputStream _out = new DataOutputStream(bytes);
            final TIntHashSet set = valueContainer.getInvalidated();
            if (set.size() > 0) {
              for (int inputId : set.toArray()) {
                myValueContainerExternalizer.saveInvalidateCommand(_out, inputId);
              }
            }
            final ValueContainer<Value> toRemove = valueContainer.getRemovedDelta();
            if (toRemove.size() > 0) {
              myValueContainerExternalizer.saveAsRemoved(_out, toRemove);
            }

            final ValueContainer<Value> toAppend = valueContainer.getAddedDelta();
            if (toAppend.size() > 0) {
              myValueContainerExternalizer.save(_out, toAppend);
            }

            map.appendData(key, new PersistentHashMap.ValueDataAppender() {
              public void append(final DataOutput out) throws IOException {
                final byte[] barr = bytes.toByteArray();
                out.write(barr);
              }
            });
          }
          else {
            // rewrite the value container for defragmentation
            map.put(key, valueContainer);
          }
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    };

    myMap = map;
  }

  public void flush() {
    l.lock();
    try {
      if (!myMap.isClosed()) {
        myCache.clear();
        myMap.force();
      }
    }
    finally {
      l.unlock();
    }
  }

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

  public Collection<Key> getKeys() throws StorageException {
    List<Key> keys = new ArrayList<Key>();
    processKeys(new CommonProcessors.CollectProcessor<Key>(keys));
    return keys;
  }

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

  public void addValue(final Key key, final int inputId, final Value value) throws StorageException {
    read(key).addValue(inputId, value);
  }

  public void removeValue(final Key key, final int inputId, final Value value) throws StorageException {
    read(key).removeValue(inputId, value);
  }

  public void removeAllValues(Key key, int inputId) throws StorageException {
    // important: assuming the key exists in the index
    read(key).removeAllValues(inputId);
  }

  private static final class ValueContainerExternalizer<T> implements DataExternalizer<ValueContainer<T>> {
    private final DataExternalizer<T> myExternalizer;

    private ValueContainerExternalizer(DataExternalizer<T> externalizer) {
      myExternalizer = externalizer;
    }

    public void save(final DataOutput out, final ValueContainer<T> container) throws IOException {
      saveImpl(out, container, false);
    }

    public void saveAsRemoved(final DataOutput out, final ValueContainer<T> container) throws IOException {
      saveImpl(out, container, true);
    }

    public void saveInvalidateCommand(final DataOutput out, int inputId) throws IOException {
      DataInputOutputUtil.writeSINT(out, -inputId);
    }

    private void saveImpl(final DataOutput out, final ValueContainer<T> container, final boolean asRemovedData) throws IOException {
      DataInputOutputUtil.writeSINT(out, container.size());
      for (final Iterator<T> valueIterator = container.getValueIterator(); valueIterator.hasNext();) {
        final T value = valueIterator.next();
        myExternalizer.save(out, value);

        final ValueContainer.IntIterator ids = container.getInputIdsIterator(value);
        if (ids != null) {
          DataInputOutputUtil.writeSINT(out, ids.size());
          while (ids.hasNext()) {
            final int id = ids.next();
            DataInputOutputUtil.writeSINT(out, asRemovedData ? -id : id);
          }
        }
        else {
          DataInputOutputUtil.writeSINT(out, 0);
        }
      }
    }

    public ValueContainerImpl<T> read(final DataInput in) throws IOException {
      DataInputStream stream = (DataInputStream)in;
      final ValueContainerImpl<T> valueContainer = new ValueContainerImpl<T>();
      
      while (stream.available() > 0) {
        final int valueCount = DataInputOutputUtil.readSINT(in);
        if (valueCount < 0) {
          valueContainer.removeAllValues(-valueCount); 
          valueContainer.setNeedsCompacting(true);
        }
        else {
          for (int valueIdx = 0; valueIdx < valueCount; valueIdx++) {
            final T value = myExternalizer.read(in);
            final int idCount = DataInputOutputUtil.readSINT(in);
            for (int i = 0; i < idCount; i++) {
              final int id = DataInputOutputUtil.readSINT(in);
              if (id < 0) {
                valueContainer.removeValue(-id, value);
                valueContainer.setNeedsCompacting(true);
              }
              else {
                valueContainer.addValue(id, value);
              }
            }
          }
        }
      }
      return valueContainer;
    }
  }
}
