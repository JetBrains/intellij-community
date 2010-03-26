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
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public class MapReduceIndex<Key, Value, Input> implements UpdatableIndex<Key,Value, Input> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.indexing.MapReduceIndex");
  private final ID<Key, Value> myIndexId;
  private final DataIndexer<Key, Value, Input> myIndexer;
  protected final IndexStorage<Key, Value> myStorage;
  private PersistentHashMap<Integer, Collection<Key>> myInputsIndex;

  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();
  
  private Factory<PersistentHashMap<Integer, Collection<Key>>> myInputsIndexFactory;


  public MapReduceIndex(@Nullable final ID<Key, Value> indexId, DataIndexer<Key, Value, Input> indexer, final IndexStorage<Key, Value> storage) {
    myIndexId = indexId;
    myIndexer = indexer;
    myStorage = storage;
  }

  public IndexStorage<Key, Value> getStorage() {
    return myStorage;
  }

  public void clear() throws StorageException {
    try {
      getWriteLock().lock();
      myStorage.clear();
      if (myInputsIndex != null) {
        final File baseFile = myInputsIndex.getBaseFile();
        try {
          myInputsIndex.close();
        }
        catch (IOException ignored) {
        }
        FileUtil.delete(baseFile);
        myInputsIndex = createInputsIndex();
      }
    }
    catch (StorageException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      getWriteLock().unlock();
    }
  }

  public void flush() throws StorageException{
    try {
      getReadLock().lock();
      final PersistentHashMap<Integer, Collection<Key>> inputsIndex = myInputsIndex;
      if (inputsIndex != null && inputsIndex.isDirty()) {
        inputsIndex.force();
      }
      myStorage.flush();
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof StorageException || cause instanceof IOException) {
        throw new StorageException(cause);
      }
      else {
        throw e;
      }
    }
    finally {
      getReadLock().unlock();
    }
  }

  public void dispose() {
    final Lock lock = getWriteLock();
    try {
      lock.lock();
      try {
        myStorage.close();
      }
      finally {
        if (myInputsIndex != null) {
          try {
            myInputsIndex.close();
          }
          catch (IOException e) {
            LOG.error(e);
          }
        }
      }
    }
    catch (StorageException e) {
      LOG.error(e);
    }
    finally {
      lock.unlock();
    }
  }

  public Lock getReadLock() {
    return myLock.readLock();
  }

  public Lock getWriteLock() {
    return myLock.writeLock();
  }

  public Collection<Key> getAllKeys() throws StorageException {
    Set<Key> allKeys = new HashSet<Key>();
    processAllKeys(new CommonProcessors.CollectProcessor<Key>(allKeys));
    return allKeys;
  }

  public boolean processAllKeys(Processor<Key> processor) throws StorageException {
    final Lock lock = getReadLock();
    try {
      lock.lock();
      return myStorage.processKeys(processor);
    }
    finally {
      lock.unlock();
    }
  }

  @NotNull
  public ValueContainer<Value> getData(final Key key) throws StorageException {
    final Lock lock = getReadLock();
    try {
      lock.lock();
      return myStorage.read(key);
    }
    finally {
      lock.unlock();
    }
  }

  public void setInputIdToDataKeysIndex(Factory<PersistentHashMap<Integer, Collection<Key>>> factory) throws IOException {
    myInputsIndexFactory = factory;
    myInputsIndex = createInputsIndex();
  }

  private PersistentHashMap<Integer, Collection<Key>> createInputsIndex() throws IOException {
    Factory<PersistentHashMap<Integer, Collection<Key>>> factory = myInputsIndexFactory;
    if (factory != null) {
      try {
        return factory.create();
      }
      catch (RuntimeException e) {
        if (e.getCause() instanceof IOException) {
          throw (IOException)e.getCause();
        }
        throw e;
      }
    }
    return null;
  }

  public final void update(final int inputId, @Nullable Input content) throws StorageException {
    assert myInputsIndex != null;

    final Map<Key, Value> data = content != null ? myIndexer.map(content) : Collections.<Key, Value>emptyMap();

    updateWithMap(inputId, data, new Callable<Collection<Key>>() {
      public Collection<Key> call() throws Exception {
        final Collection<Key> oldKeys = myInputsIndex.get(inputId);
        return oldKeys == null? Collections.<Key>emptyList() : oldKeys;
      }
    });
  }

  protected void updateWithMap(final int inputId, final Map<Key, Value> newData, Callable<Collection<Key>> oldKeysGetter) throws StorageException {
    getWriteLock().lock();
    try {
      try {
        for (Key key : oldKeysGetter.call()) {
          myStorage.removeAllValues(key, inputId);
        }
      }
      catch (Exception e) {
        throw new StorageException(e);
      }
      // add new values
      for (Map.Entry<Key, Value> entry : newData.entrySet()) {
        myStorage.addValue(entry.getKey(), inputId, entry.getValue());
      }
      if (myInputsIndex != null) {
        try {
          final Set<Key> newKeys = newData.keySet();
          if (newKeys.size() > 0) {
            myInputsIndex.put(inputId, newKeys);
          }
          else {
            myInputsIndex.remove(inputId);
          }
        }
        catch (IOException e) {
          throw new StorageException(e);
        }
      }
    }
    finally {
      getWriteLock().unlock();
    }
  }

}
