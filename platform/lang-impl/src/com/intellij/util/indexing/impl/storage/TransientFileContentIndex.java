// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.storage;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import com.intellij.util.indexing.storage.SnapshotInputMappingIndex;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;

public class TransientFileContentIndex<Key, Value> extends VfsAwareMapReduceIndex<Key, Value> {
  private static final Logger LOG = Logger.getInstance(TransientFileContentIndex.class);

  private final AtomicBoolean myInMemoryMode = new AtomicBoolean();
  private final ConcurrentIntObjectMap<Map<Key, Value>> myInMemoryKeysAndValues =
    ConcurrentCollectionFactory.createConcurrentIntObjectMap();


  public TransientFileContentIndex(@NotNull FileBasedIndexExtension<Key, Value> extension,
                                   @NotNull VfsAwareIndexStorageLayout<Key, Value> indexStorageLayout,
                                   @Nullable ReadWriteLock lock)
    throws IOException {
    super(extension,
          new VfsAwareIndexStorageLayout<>() {
            @Override
            public @NotNull IndexStorage<Key, Value> openIndexStorage() throws IOException {
              return new TransientChangesIndexStorage<>(indexStorageLayout.openIndexStorage(), extension);
            }

            @Override
            public @Nullable SnapshotInputMappingIndex<Key, Value, FileContent> createOrClearSnapshotInputMappings() throws IOException {
              return indexStorageLayout.createOrClearSnapshotInputMappings();
            }

            @Override
            public void clearIndexData() {
              indexStorageLayout.clearIndexData();
            }

            @Override
            public @Nullable ForwardIndex openForwardIndex() throws IOException {
              return indexStorageLayout.openForwardIndex();
            }

            @Override
            public @Nullable ForwardIndexAccessor<Key, Value> getForwardIndexAccessor() throws IOException {
              return indexStorageLayout.getForwardIndexAccessor();
            }
          },
          lock);
    installMemoryModeListener();
  }

  @NotNull
  @Override
  protected InputDataDiffBuilder<Key, Value> getKeysDiffBuilder(int inputId) throws IOException {
    if (myInMemoryMode.get()) {
      Map<Key, Value> keysAndValues = myInMemoryKeysAndValues.get(inputId);
      if (keysAndValues != null) {
        return getKeysDiffBuilder(inputId, keysAndValues);
      }
    }
    return super.getKeysDiffBuilder(inputId);
  }

  @Override
  protected void updateForwardIndex(int inputId, @NotNull InputData<Key, Value> data) throws IOException {
    if (myInMemoryMode.get()) {
      myInMemoryKeysAndValues.put(inputId, data.getKeyValues());
    } else {
      super.updateForwardIndex(inputId, data);
    }
  }

  @Override
  @Nullable
  protected Map<Key, Value> getNullableIndexedData(int fileId) throws IOException, StorageException {
    if (myInMemoryMode.get()) {
      Map<Key, Value> map = myInMemoryKeysAndValues.get(fileId);
      if (map != null) return map;
    }
    return super.getNullableIndexedData(fileId);
  }

  private void installMemoryModeListener() {
    IndexStorage<Key, Value> storage = getStorage();
    if (storage instanceof TransientChangesIndexStorage) {
      ((TransientChangesIndexStorage<Key, Value>)storage).addBufferingStateListener(new TransientChangesIndexStorage.BufferingStateListener() {
        @Override
        public void bufferingStateChanged(boolean newState) {
          myInMemoryMode.set(newState);
        }

        @Override
        public void memoryStorageCleared() {
          myInMemoryKeysAndValues.clear();
        }
      });
    }
  }

  @Override
  public void setBufferingEnabled(boolean enabled) {
    ((TransientChangesIndexStorage<Key, Value>)getStorage()).setBufferingEnabled(enabled);
  }

  @Override
  public void removeTransientDataForFile(int inputId) {
    if (IndexDebugProperties.DEBUG) {
      LOG.assertTrue(ProgressManager.getInstance().isInNonCancelableSection());
    }
    getLock().writeLock().lock();
    try {
      Map<Key, Value> keyValueMap = myInMemoryKeysAndValues.remove(inputId);
      if (keyValueMap == null) return;

      try {
        removeTransientDataForInMemoryKeys(inputId, keyValueMap);
        InputDataDiffBuilder<Key, Value> builder = getKeysDiffBuilder(inputId);
        removeTransientDataForKeys(inputId, builder);
      } catch (IOException throwable) {
        throw new RuntimeException(throwable);
      }
    } finally {
      getLock().writeLock().unlock();
    }
  }

  protected void removeTransientDataForInMemoryKeys(int inputId, @NotNull Map<Key, Value> map) throws IOException {
    removeTransientDataForKeys(inputId, getKeysDiffBuilder(inputId, map));
  }

  @Override
  public void removeTransientDataForKeys(int inputId, @NotNull InputDataDiffBuilder<Key, Value> diffBuilder) {
    TransientChangesIndexStorage<Key, Value> memoryIndexStorage = (TransientChangesIndexStorage<Key, Value>)getStorage();
    boolean modified = false;
    for (Key key : ((DirectInputDataDiffBuilder<Key, Value>)diffBuilder).getKeys()) {
      if (memoryIndexStorage.clearMemoryMapForId(key, inputId) && !modified) {
        modified = true;
      }
    }
    if (modified) {
      incrementModificationStamp();
    }
  }


  @Override
  public void cleanupMemoryStorage() {
    TransientChangesIndexStorage<Key, Value> memStorage = (TransientChangesIndexStorage<Key, Value>)getStorage();
    //no synchronization on index write-lock, should be performed fast as possible since executed in write-action
    if (memStorage.clearMemoryMap()) {
      incrementModificationStamp();
    }
    memStorage.fireMemoryStorageCleared();
  }

  @TestOnly
  @Override
  public void cleanupForNextTest() {
    IndexStorage<Key, Value> memStorage = getStorage();
    ConcurrencyUtil.withLock(getLock().readLock(), () -> memStorage.clearCaches());
  }

}
