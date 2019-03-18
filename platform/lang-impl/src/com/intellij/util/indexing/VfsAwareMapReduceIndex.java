// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.indexing;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.PersistentMapBasedForwardIndex;
import com.intellij.util.io.*;
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
  private final SnapshotInputMappingIndex<Key, Value, Input> mySnapshotInputMappings;

  public VfsAwareMapReduceIndex(@NotNull IndexExtension<Key, Value, Input> extension,
                                @NotNull IndexStorage<Key, Value> storage) throws IOException {
    this(extension,
         storage,
         getForwardIndex(extension),
         hasSnapshotMapping(extension) ? new SnapshotInputMappings<>(extension) : null);
    if (!(myIndexId instanceof ID<?, ?>)) {
      throw new IllegalArgumentException("myIndexId should be instance of com.intellij.util.indexing.ID");
    }
  }

  public VfsAwareMapReduceIndex(@NotNull IndexExtension<Key, Value, Input> extension,
                                @NotNull IndexStorage<Key, Value> storage,
                                @Nullable ForwardIndex<Key, Value> forwardIndex,
                                @Nullable SnapshotInputMappings<Key, Value, Input> snapshotInputMappings) throws IOException {
    super(extension,
          storage,
          snapshotInputMappings != null ? new SharedMapForwardIndex(extension, snapshotInputMappings.getForwardIndexAccessor()): null,
          snapshotInputMappings != null ? snapshotInputMappings.getForwardIndexAccessor(): null,
          forwardIndex);
    SharedIndicesData.registerIndex((ID<Key, Value>)myIndexId, extension);
    mySnapshotInputMappings = snapshotInputMappings;
    installMemoryModeListener();
  }

  public VfsAwareMapReduceIndex(@NotNull IndexExtension<Key, Value, Input> extension,
                                @NotNull IndexStorage<Key, Value> storage,
                                @Nullable com.intellij.util.indexing.impl.forward.ForwardIndex forwardIndex,
                                @Nullable ForwardIndexAccessor<Key, Value, ?, Input> forwardIndexAccessor) {
    super(extension, storage, forwardIndex, forwardIndexAccessor);
    SharedIndicesData.registerIndex((ID<Key, Value>)myIndexId, extension);
    mySnapshotInputMappings = null;
    installMemoryModeListener();
  }

  private static <Key, Value> boolean hasSnapshotMapping(@NotNull IndexExtension<Key, Value, ?> indexExtension) {
    return indexExtension instanceof FileBasedIndexExtension &&
           ((FileBasedIndexExtension<Key, Value>)indexExtension).hasSnapshotMapping() &&
           IdIndex.ourSnapshotMappingsEnabled;
  }

  @NotNull
  @Override
  protected Map<Key, Value> mapInput(@Nullable Input content) {
    if (mySnapshotInputMappings != null && !myInMemoryMode.get()) {
      return mySnapshotInputMappings.readDataOrMap(content);
    }
    return super.mapInput(content);
  }

  @NotNull
  @Override
  protected InputDataDiffBuilder<Key, Value> getKeysDiffBuilder(int inputId) throws IOException {
    if (mySnapshotInputMappings != null && !myInMemoryMode.get()) {
      return super.getKeysDiffBuilder(inputId);
    }
    if (myInMemoryMode.get()) {
      synchronized (myInMemoryKeysAndValues) {
        Map<Key, Value> keysAndValues = myInMemoryKeysAndValues.get(inputId);
        if (keysAndValues != null) {
          return new MapInputDataDiffBuilder<>(inputId, keysAndValues);
        }
      }
    }
    return super.getKeysDiffBuilder(inputId);
  }

  @Override
  protected void updateForwardIndex(int inputId, @NotNull Map<Key, Value> data, @Nullable Object forwardIndexData) throws IOException {
    if (myInMemoryMode.get()) {
      synchronized (myInMemoryKeysAndValues) {
        myInMemoryKeysAndValues.put(inputId, data);
      }
    } else {
      super.updateForwardIndex(inputId, data, forwardIndexData);
    }
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

        InputDataDiffBuilder<Key, Value> builder = getKeysDiffBuilder(inputId);
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
            (key, inputId1) -> diskKeySet.add(key)
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

  public void removeTransientDataForKeys(int inputId, @NotNull Collection<? extends Key> keys) throws IOException {
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
    return new MyMapBasedForwardIndex<>(indexExtension);
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
        return new PersistentHashMap<>(indexStorageFile, EnumeratorIntegerDescriptor.INSTANCE, new InputMapExternalizer<>(myIndexExtension));
      } finally {
        PersistentHashMapValueStorage.CreationTimeOptions.HAS_NO_CHUNKS.set(Boolean.FALSE);
      }
    }

    @NotNull
    @Override
    protected InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId, @Nullable Map<Key, Value> map) {
      return new MapInputDataDiffBuilder<>(inputId, map);
    }

    @NotNull
    @Override
    protected Map<Key, Value> convertToMapValueType(int inputId, @NotNull Map<Key, Value> map) {
      return map;
    }
  }

  public static <Key, Value> com.intellij.util.indexing.impl.forward.ForwardIndex createForwardIndex(IndexExtension<Key, Value, ?> extension) throws IOException {
    final File indexStorageFile = IndexInfrastructure.getInputIndexStorageFile((ID<?, ?>)extension.getName());
    return new PersistentMapBasedForwardIndex(indexStorageFile, false);
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
}
