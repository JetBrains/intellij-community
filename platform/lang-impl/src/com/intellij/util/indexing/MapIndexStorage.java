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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.*;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
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
  private final boolean myBuildKeyHashToVirtualFileMapping;
  private PersistentMap<Key, ValueContainer<Value>> myMap;
  private PersistentBTreeEnumerator<int[]> myKeyHashToVirtualFileMapping;
  private SLRUCache<Key, ChangeTrackingValueContainer<Value>> myCache;
  private final File myStorageFile;
  private final KeyDescriptor<Key> myKeyDescriptor;
  private final int myCacheSize;

  private final Lock l = new ReentrantLock();
  private final DataExternalizer<Value> myDataExternalizer;
  private boolean myHighKeySelectivity;
  private final LowMemoryWatcher myLowMemoryFlusher = LowMemoryWatcher.register(new Runnable() {
    @Override
    public void run() {
      l.lock();
      try {
        if (!myMap.isClosed()) {
          myCache.clear();
          if (myMap.isDirty()) myMap.force();
        }
      } finally {
        l.unlock();
      }
    }
  });

  public MapIndexStorage(@NotNull File storageFile,
                         @NotNull KeyDescriptor<Key> keyDescriptor,
                         @NotNull DataExternalizer<Value> valueExternalizer,
                         final int cacheSize
  ) throws IOException {
    this(storageFile, keyDescriptor, valueExternalizer, cacheSize, false, false);
  }

  public MapIndexStorage(@NotNull File storageFile,
                         @NotNull KeyDescriptor<Key> keyDescriptor,
                         @NotNull DataExternalizer<Value> valueExternalizer,
                         final int cacheSize,
                         boolean highKeySelectivity,
                         boolean buildKeyHashToVirtualFileMapping
                         ) throws IOException {

    myStorageFile = storageFile;
    myKeyDescriptor = keyDescriptor;
    myCacheSize = cacheSize;
    myDataExternalizer = valueExternalizer;
    myHighKeySelectivity = highKeySelectivity;
    myBuildKeyHashToVirtualFileMapping = buildKeyHashToVirtualFileMapping && FileBasedIndex.ourEnableTracingOfKeyHashToVirtualFileMapping;
    initMapAndCache();
  }

  private void initMapAndCache() throws IOException {
    final ValueContainerMap<Key, Value> map = new ValueContainerMap<Key, Value>(myStorageFile, myKeyDescriptor, myDataExternalizer);
    myCache = new SLRUCache<Key, ChangeTrackingValueContainer<Value>>(myCacheSize, (int)(Math.ceil(myCacheSize * 0.25)) /* 25% from the main cache size*/) {
      @Override
      @NotNull
      public ChangeTrackingValueContainer<Value> createValue(final Key key) {
        return new ChangeTrackingValueContainer<Value>(new ChangeTrackingValueContainer.Initializer<Value>() {
          @NotNull
          @Override
          public Object getLock() {
            return map.getDataAccessLock();
          }

          @Nullable
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
      protected void onDropFromCache(final Key key, @NotNull final ChangeTrackingValueContainer<Value> valueContainer) {
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

    myKeyHashToVirtualFileMapping = myBuildKeyHashToVirtualFileMapping ? new KeyHash2VirtualFileEnumerator(getProjectFile()) : null;
  }

  private File getProjectFile() {
    return new File(myStorageFile.getPath() + ".project");
  }

  @Override
  public void flush() {
    l.lock();
    try {
      if (!myMap.isClosed() && myMap.isDirty()) {
        myCache.clear();
        myMap.force();
      }
      if (myKeyHashToVirtualFileMapping != null) myKeyHashToVirtualFileMapping.force();
    }
    finally {
      l.unlock();
    }
  }

  @Override
  public void close() throws StorageException {
    try {
      myLowMemoryFlusher.stop();
      flush();
      if (myKeyHashToVirtualFileMapping != null) myKeyHashToVirtualFileMapping.close();
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
      if (myKeyHashToVirtualFileMapping != null) myKeyHashToVirtualFileMapping.close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
    try {
      FileUtil.delete(myStorageFile);
      if (myKeyHashToVirtualFileMapping != null) IOUtil.deleteAllFilesStartingWith(getProjectFile());
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
  public boolean processKeys(final Processor<Key> processor, final IdFilter idFilter) throws StorageException {
    l.lock();
    try {
      myCache.clear(); // this will ensure that all new keys are made into the map
      if (myBuildKeyHashToVirtualFileMapping && idFilter != null) {
        final TIntHashSet hashMaskSet = new TIntHashSet(1000);
        long l = System.currentTimeMillis();
        myKeyHashToVirtualFileMapping.iterateData(new Processor<int[]>() {
          @Override
          public boolean process(int[] key) {
            if (!idFilter.containsFileId(key[1])) return true;
            hashMaskSet.add(key[0]);
            ProgressManager.checkCanceled();
            return true;
          }
        });
        if (LOG.isDebugEnabled()) {
          LOG.debug("Scanned keyHashToVirtualFileMapping of " + myStorageFile + " for " + (System.currentTimeMillis() - l));
        }
        return myMap.processKeys(new Processor<Key>() {
          @Override
          public boolean process(Key key) {
            if (!hashMaskSet.contains(myKeyDescriptor.getHashCode(key))) return true;
            return processor.process(key);
          }
        });
      }
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

  @NotNull
  @Override
  public Collection<Key> getKeys() throws StorageException {
    List<Key> keys = new ArrayList<Key>();
    processKeys(new CommonProcessors.CollectProcessor<Key>(keys), null);
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
      if (myKeyHashToVirtualFileMapping != null) {
        myKeyHashToVirtualFileMapping.enumerate(new int[] { myKeyDescriptor.getHashCode(key), inputId });
      }

      myMap.markDirty();
      if (!myHighKeySelectivity) {
        read(key).addValue(inputId, value);
        return;
      }

      ChangeTrackingValueContainer<Value> cached;
      try {
        l.lock();
        cached = myCache.getIfCached(key);
      } finally {
        l.unlock();
      }

      if (cached != null) {
        cached.addValue(inputId, value);
        return;
      }
      // do not pollute the cache with highly selective data
      ChangeTrackingValueContainer<Value> valueContainer = new ChangeTrackingValueContainer<Value>(null);
      valueContainer.addValue(inputId, value);
      myMap.put(key, valueContainer);
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

  private static class IntPairInArrayKeyDescriptor implements KeyDescriptor<int[]>, DifferentSerializableBytesImplyNonEqualityPolicy {
    @Override
    public void save(DataOutput out, int[] value) throws IOException {
      DataInputOutputUtil.writeINT(out, value[0]);
      DataInputOutputUtil.writeINT(out, value[1]);
    }

    @Override
    public int[] read(DataInput in) throws IOException {
      return new int[] {DataInputOutputUtil.readINT(in), DataInputOutputUtil.readINT(in)};
    }

    @Override
    public int getHashCode(int[] value) {
      return value[0] * 31 + value[1];
    }

    @Override
    public boolean isEqual(int[] val1, int[] val2) {
      return val1[0] == val2[0] && val1[1] == val2[1];
    }
  }

  private static class KeyHash2VirtualFileEnumerator extends PersistentBTreeEnumerator<int[]> {
    public KeyHash2VirtualFileEnumerator(File projectFile) throws IOException {
      super(projectFile, new IntPairInArrayKeyDescriptor(), 4096);
    }
  }
}
