// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.indexing;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.io.*;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

/**
 * @author Eugene Zhuravlev
 */
public class VfsAwareMapReduceIndex<Key, Value, Input> extends MapReduceIndex<Key, Value, Input> implements UpdatableIndex<Key, Value, Input>{
  private static final Logger LOG = Logger.getInstance(VfsAwareMapReduceIndex.class);

  static {
    if (!DebugAssertions.DEBUG) {
      final Application app = ApplicationManager.getApplication();
      DebugAssertions.DEBUG = app.isEAP() || app.isInternal();
    }
  }

  private final AtomicBoolean myInMemoryMode = new AtomicBoolean();
  private final TIntObjectHashMap<Map<Key, Value>> myInMemoryKeysAndValues = new TIntObjectHashMap<>();
  private final SnapshotInputMappings<Key, Value, Input> mySnapshotInputMappings;

  public VfsAwareMapReduceIndex(@NotNull IndexExtension<Key, Value, Input> extension,
                                @NotNull IndexStorage<Key, Value> storage) throws IOException {
    this(extension, storage, getForwardIndex(extension));
    if (!(myIndexId instanceof ID<?, ?>)) {
      throw new IllegalArgumentException("myIndexId should be instance of com.intellij.util.indexing.ID");
    }
  }

  public VfsAwareMapReduceIndex(@NotNull IndexExtension<Key, Value, Input> extension,
                                @NotNull IndexStorage<Key, Value> storage,
                                @Nullable ForwardIndex<Key, Value> forwardIndex) throws IOException {
    super(extension, storage, forwardIndex);
    SharedIndicesData.registerIndex((ID<Key, Value>)myIndexId, extension);
    mySnapshotInputMappings = myForwardIndex == null && hasSnapshotMapping(extension)?
                              new SnapshotInputMappings<>(extension) :
                              null;
    installMemoryModeListener();
  }

  private static <Key, Value> boolean hasSnapshotMapping(@NotNull IndexExtension<Key, Value, ?> indexExtension) {
    return indexExtension instanceof FileBasedIndexExtension &&
           ((FileBasedIndexExtension<Key, Value>)indexExtension).hasSnapshotMapping() &&
           IdIndex.ourSnapshotMappingsEnabled;
  }

  @NotNull
  @Override
  protected UpdateData<Key, Value> calculateUpdateData(int inputId, @Nullable Input content) {
    Map<Key, Value> data;
    int hashId;
    final boolean isContentPhysical = isContentPhysical(content);
    if (mySnapshotInputMappings != null && content != null && isContentPhysical) {
      final SnapshotInputMappings.Snapshot<Key, Value> snapshot = mySnapshotInputMappings.readPersistentDataOrMap(content);
      data = snapshot.getData();
      hashId = snapshot.getHashId();
    }
    else {
      data = mapInput(content);
      hashId = 0;
    }
    return createUpdateData(data, () -> {
      if (mySnapshotInputMappings != null && isContentPhysical) {
        return new MapInputDataDiffBuilder<>(inputId, mySnapshotInputMappings.readInputKeys(inputId));
      }
      if (myInMemoryMode.get()) {
        synchronized (myInMemoryKeysAndValues) {
          Map<Key, Value> keysAndValues = myInMemoryKeysAndValues.get(inputId);
          if (keysAndValues != null) {
            return new MapInputDataDiffBuilder<>(inputId, keysAndValues);
          }
        }

        if (mySnapshotInputMappings != null) {
          return new MapInputDataDiffBuilder<>(inputId, mySnapshotInputMappings.readInputKeys(inputId));
        }
      }
      return getKeysDiffBuilder(inputId);
    }, () -> {
      if (myInMemoryMode.get()) {
        synchronized (myInMemoryKeysAndValues) {
          myInMemoryKeysAndValues.put(inputId, data);
        }
      } else {
        if (mySnapshotInputMappings != null ) {
          mySnapshotInputMappings.putInputHash(inputId, hashId);
        } else {
          myForwardIndex.putInputData(inputId, data);
        }
      }
    });
  }

  @Override
  public void setIndexedStateForFile(int fileId, @NotNull VirtualFile file) {
    IndexingStamp.setFileIndexedStateCurrent(fileId, (ID<?, ?>)myIndexId);
  }

  @Override
  public void resetIndexedStateForFile(int fileId) {
    IndexingStamp.setFileIndexedStateOutdated(fileId, (ID<?, ?>)myIndexId);
  }

  @Override
  public boolean isIndexedStateForFile(int fileId, @NotNull VirtualFile file) {
    return IndexingStamp.isFileIndexedStateCurrent(fileId, (ID<?, ?>)myIndexId);
  }

  @Override
  public void removeTransientDataForFile(int inputId) {
    Lock lock = getWriteLock();
    lock.lock();
    try {
      Collection<Key> keyCollection;
      synchronized (myInMemoryKeysAndValues) {
        Map<Key, Value> keyValueMap = myInMemoryKeysAndValues.remove(inputId);
        keyCollection = keyValueMap != null ? keyValueMap.keySet() : null;
      }

      if (keyCollection == null) return;

      try {
        removeTransientDataForKeys(inputId, keyCollection);

        InputDataDiffBuilder<Key, Value> builder;
        if (mySnapshotInputMappings != null) {
          builder =  new MapInputDataDiffBuilder<>(inputId, mySnapshotInputMappings.readInputKeys(inputId));
        } else {
          builder = getKeysDiffBuilder(inputId);
        }

        if (builder instanceof CollectionInputDataDiffBuilder<?, ?>) {
          Collection<Key> keyCollectionFromDisk = ((CollectionInputDataDiffBuilder<Key, Value>)builder).getSeq();
          if (keyCollectionFromDisk != null) {
            removeTransientDataForKeys(inputId, keyCollectionFromDisk);
          }
        } else {
          Set<Key> diskKeySet = new THashSet<>();

          builder.differentiate(
            Collections.emptyMap(),
            (key, value, inputId1) -> {
            },
            (key, value, inputId1) -> {},
            (key, inputId1) -> {
              diskKeySet.add(key);
            }
          );
          removeTransientDataForKeys(inputId, diskKeySet);
        }
      } catch (Throwable throwable) {
        throw new RuntimeException(throwable);
      }
    } finally {
      lock.unlock();
    }
  }

  public void removeTransientDataForKeys(int inputId, @NotNull Collection<? extends Key> keys) {
    MemoryIndexStorage memoryIndexStorage = (MemoryIndexStorage)getStorage();
    for (Key key : keys) {
      memoryIndexStorage.clearMemoryMapForId(key, inputId);
    }
  }

  @Override
  public boolean processAllKeys(@NotNull Processor<? super Key> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter idFilter) throws StorageException {
    final Lock lock = getReadLock();
    lock.lock();
    try {
      return ((VfsAwareIndexStorage<Key, Value>)myStorage).processKeys(processor, scope, idFilter);
    }
    finally {
      lock.unlock();
    }
  }

  @Override
  public void checkCanceled() {
    ProgressManager.checkCanceled();
  }

  @Override
  protected void requestRebuild(@NotNull Throwable ex) {
    Runnable action = () -> FileBasedIndex.getInstance().requestRebuild((ID<?, ?>)myIndexId, ex);
    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() || application.isHeadlessEnvironment()) {
      // avoid deadlock due to synchronous update in DumbServiceImpl#queueTask
      TransactionGuard.getInstance().submitTransactionLater(application, action);
    } else {
      action.run();
    }
  }

  @Override
  protected void doClear() throws StorageException, IOException {
    super.doClear();
    if (mySnapshotInputMappings != null) {
      try {
        mySnapshotInputMappings.clear();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  protected void doFlush() throws IOException, StorageException {
    super.doFlush();
    if (mySnapshotInputMappings != null) mySnapshotInputMappings.flush();
  }

  @Override
  protected void doDispose() throws StorageException {
    super.doDispose();

    if (mySnapshotInputMappings != null) {
      try {
        mySnapshotInputMappings.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @Nullable
  private static <Key, Value> ForwardIndex<Key, Value> getForwardIndex(@NotNull IndexExtension<Key, Value, ?> indexExtension)
    throws IOException {
    if (hasSnapshotMapping(indexExtension)) return null;

    if (!(indexExtension instanceof CustomInputsIndexFileBasedIndexExtension)) {
      return new MyMapBasedForwardIndex<>(indexExtension);
    }
    KeyCollectionBasedForwardIndex<Key, Value> backgroundIndex =
      !SharedIndicesData.ourFileSharedIndicesEnabled || SharedIndicesData.DO_CHECKS ? new MyForwardIndex<>(indexExtension) : null;
    return new SharedMapBasedForwardIndex<>(indexExtension, backgroundIndex);
  }

  private static class MyMapBasedForwardIndex<Key, Value> extends MapBasedForwardIndex<Key, Value, Map<Key, Value>> {
    protected MyMapBasedForwardIndex(IndexExtension<Key, Value, ?> indexExtension) throws IOException {
      super(indexExtension);
    }

    @NotNull
    @Override
    public PersistentHashMap<Integer, Map<Key, Value>> createMap() throws IOException {
      PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(Boolean.TRUE);
      try {
        final File indexStorageFile = IndexInfrastructure.getInputIndexStorageFile((ID<?, ?>)myIndexExtension.getName());
        return new PersistentHashMap<>(indexStorageFile, EnumeratorIntegerDescriptor.INSTANCE, new MapDataExternalizer<>(myIndexExtension));
      } finally {
        PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(Boolean.FALSE);
      }
    }

    @Override
    protected InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId, Map<Key, Value> map) throws IOException {
      return new MapInputDataDiffBuilder<>(inputId, map);
    }

    @Override
    protected Map<Key, Value> convertToMapValueType(int inputId, Map<Key, Value> map) throws IOException {
      return map;
    }
  }

  private static class MyForwardIndex<Key, Value> extends KeyCollectionBasedForwardIndex<Key, Value> {
    protected MyForwardIndex(IndexExtension<Key, Value, ?> indexExtension) throws IOException {
      super(indexExtension);
    }

    @NotNull
    @Override
    public PersistentHashMap<Integer, Collection<Key>> createMap() throws IOException {
      PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(Boolean.TRUE);
      try {
        return createIdToDataKeysIndex(myIndexExtension);
      } finally {
        PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(Boolean.FALSE);
      }
    }

    @NotNull
    private static <K> PersistentHashMap<Integer, Collection<K>> createIdToDataKeysIndex(@NotNull IndexExtension<K, ?, ?> extension) throws IOException {
      final File indexStorageFile = IndexInfrastructure.getInputIndexStorageFile((ID<?, ?>)extension.getName());
      return new PersistentHashMap<>(indexStorageFile, EnumeratorIntegerDescriptor.INSTANCE, createInputsIndexExternalizer(extension));
    }
  }

  private boolean isContentPhysical(Input content) {
    return content == null ||
           (content instanceof UserDataHolder &&
            FileBasedIndexImpl.ourPhysicalContentKey.get((UserDataHolder)content, Boolean.FALSE));
  }

  private void installMemoryModeListener() {
    IndexStorage<Key, Value> storage = getStorage();
    if (storage instanceof MemoryIndexStorage) {
      ((MemoryIndexStorage)storage).addBufferingStateListener(new MemoryIndexStorage.BufferingStateListener() {
        @Override
        public void bufferingStateChanged(boolean newState) {
          myInMemoryMode.set(newState);
        }

        @Override
        public void memoryStorageCleared() {
          synchronized (myInMemoryKeysAndValues) {
            myInMemoryKeysAndValues.clear();
          }
        }
      });
    }
  }

  protected static <K> DataExternalizer<Collection<K>> createInputsIndexExternalizer(IndexExtension<K, ?, ?> extension) {
    return extension instanceof CustomInputsIndexFileBasedIndexExtension
           ? ((CustomInputsIndexFileBasedIndexExtension<K>)extension).createExternalizer()
           : new InputIndexDataExternalizer<>(extension.getKeyDescriptor(), extension.getName());
  }

  static class MapDataExternalizer<Key, Value> implements DataExternalizer<Map<Key, Value>> {
    final DataExternalizer<Value> myValueExternalizer;
    private final DataExternalizer<Collection<Key>> mySnapshotIndexExternalizer;

    MapDataExternalizer(IndexExtension<Key, Value, ?> extension) {
      myValueExternalizer = extension.getValueExternalizer();
      mySnapshotIndexExternalizer = createInputsIndexExternalizer(extension);
    }

    @Override
    public void save(@NotNull DataOutput stream, Map<Key, Value> data) throws IOException {
      int size = data.size();
      DataInputOutputUtil.writeINT(stream, size);

      if (size > 0) {
        THashMap<Value, List<Key>> values = new THashMap<>();
        List<Key> keysForNullValue = null;
        for (Map.Entry<Key, Value> e : data.entrySet()) {
          Value value = e.getValue();

          List<Key> keys = value != null ? values.get(value):keysForNullValue;
          if (keys == null) {
            if (value != null) values.put(value, keys = new SmartList<>());
            else keys = keysForNullValue = new SmartList<>();
          }
          keys.add(e.getKey());
        }

        if (keysForNullValue != null) {
          myValueExternalizer.save(stream, null);
          mySnapshotIndexExternalizer.save(stream, keysForNullValue);
        }

        for(Value value:values.keySet()) {
          myValueExternalizer.save(stream, value);
          mySnapshotIndexExternalizer.save(stream, values.get(value));
        }
      }
    }

    @Override
    public Map<Key, Value> read(@NotNull DataInput in) throws IOException {
      int pairs = DataInputOutputUtil.readINT(in);
      if (pairs == 0) return Collections.emptyMap();
      Map<Key, Value> result = new THashMap<>(pairs);
      while (((InputStream)in).available() > 0) {
        Value value = myValueExternalizer.read(in);
        Collection<Key> keys = mySnapshotIndexExternalizer.read(in);
        for(Key k:keys) result.put(k, value);
      }
      return result;
    }
  }
}
