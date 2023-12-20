// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.indexing.impl.storage;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This storage is needed for indexing yet unsaved data without saving those changes to 'main' backend storage
 */
public final class TransientChangesIndexStorage<Key, Value> implements VfsAwareIndexStorage<Key, Value> {
  private static final Logger LOG = Logger.getInstance(TransientChangesIndexStorage.class);
  private final Map<Key, TransientChangeTrackingValueContainer<Value>> myMap;
  private final @NotNull VfsAwareIndexStorage<Key, Value> myBackendStorage;
  private final List<BufferingStateListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final @NotNull ID<?, ?> myIndexId;
  private boolean myBufferingEnabled;

  public interface BufferingStateListener {
    void bufferingStateChanged(boolean newState);

    void memoryStorageCleared();
  }

  public TransientChangesIndexStorage(@NotNull IndexStorage<Key, Value> backend, @NotNull FileBasedIndexExtension<Key, Value> extension) {
    myBackendStorage = (VfsAwareIndexStorage<Key, Value>)backend;
    myIndexId = extension.getName();
    myMap = ConcurrentCollectionFactory.createConcurrentMap(IndexStorageUtil.adaptKeyDescriptorToStrategy(extension.getKeyDescriptor()));
  }

  @NotNull
  VfsAwareIndexStorage<Key, Value> getBackendStorage() {
    return myBackendStorage;
  }

  public void addBufferingStateListener(@NotNull BufferingStateListener listener) {
    myListeners.add(listener);
  }

  public void setBufferingEnabled(boolean enabled) {
    final boolean wasEnabled = myBufferingEnabled;
    LOG.assertTrue(wasEnabled != enabled);

    myBufferingEnabled = enabled;
    for (BufferingStateListener listener : myListeners) {
      listener.bufferingStateChanged(enabled);
    }
  }

  public boolean clearMemoryMap() {
    boolean modified = !myMap.isEmpty();
    myMap.clear();
    if (modified && FileBasedIndexEx.doTraceStubUpdates(myIndexId)) {
      LOG.info("clearMemoryMap,index=" + myIndexId);
    }
    return modified;
  }

  public boolean clearMemoryMapForId(Key key, int fileId) {
    TransientChangeTrackingValueContainer<Value> container = myMap.get(key);
    if (container != null) {
      container.dropAssociatedValue(fileId);
      if (FileBasedIndexEx.doTraceStubUpdates(myIndexId)) {
        LOG.info("clearMemoryMapForId,inputId=" + fileId + ",index=" + myIndexId + ",key=" + key);
      }
      return true;
    }
    return false;
  }

  public void fireMemoryStorageCleared() {
    for (BufferingStateListener listener : myListeners) {
      listener.memoryStorageCleared();
    }
  }

  @Override
  public void clearCaches() {
    try {
      if (myMap.isEmpty()) return;

      if (IndexDebugProperties.DEBUG || FileBasedIndexEx.doTraceStubUpdates(myIndexId)) {
        LOG.info("clearCaches,index=" + myIndexId + ",number of items:" + myMap.size());
      }

      for (ChangeTrackingValueContainer<Value> v : myMap.values()) {
        v.dropMergedData();
      }
    } finally {
      myBackendStorage.clearCaches();
    }
  }

  @Override
  public void close() throws StorageException {
    myBackendStorage.close();
  }

  @Override
  public void clear() throws StorageException {
    clearMemoryMap();
    myBackendStorage.clear();
  }

  @Override
  public void flush() throws IOException {
    myBackendStorage.flush();
  }

  @Override
  public boolean isDirty() {
    return myBackendStorage.isDirty();
  }

  @Override
  public boolean processKeys(final @NotNull Processor<? super Key> processor, GlobalSearchScope scope, IdFilter idFilter) throws StorageException {
    final Set<Key> stopList = new HashSet<>();

    Processor<Key> decoratingProcessor = key -> {
      if (stopList.contains(key)) return true;

      final UpdatableValueContainer<Value> container = myMap.get(key);
      if (container != null && container.size() == 0) {
        return true;
      }
      return processor.process(key);
    };

    for (Key key : myMap.keySet()) {
      if (!decoratingProcessor.process(key)) {
        return false;
      }
      stopList.add(key);
    }
    return myBackendStorage.processKeys(stopList.isEmpty() ? processor : decoratingProcessor, scope, idFilter);
  }

  @Override
  public void addValue(final Key key, final int inputId, final Value value) throws StorageException {
    if (FileBasedIndexEx.doTraceStubUpdates(myIndexId)) {
      LOG.info("addValue,inputId=" + inputId + ",index=" + myIndexId + ",inMemory=" + myBufferingEnabled + "," + value);
    }

    if (myBufferingEnabled) {
      getMemValueContainer(key).addValue(inputId, value);
      return;
    }
    final ChangeTrackingValueContainer<Value> valueContainer = myMap.get(key);
    if (valueContainer != null) {
      valueContainer.dropMergedData();
    }

    myBackendStorage.addValue(key, inputId, value);
  }

  @Override
  public void removeAllValues(@NotNull Key key, int inputId) throws StorageException {
    if (FileBasedIndexEx.doTraceStubUpdates(myIndexId)) {
      LOG.info("removeAllValues,inputId=" + inputId + ",index=" + myIndexId + ",inMemory=" + myBufferingEnabled);
    }

    if (myBufferingEnabled) {
      getMemValueContainer(key).removeAssociatedValue(inputId);
      return;
    }
    final ChangeTrackingValueContainer<Value> valueContainer = myMap.get(key);
    if (valueContainer != null) {
      valueContainer.dropMergedData();
    }

    myBackendStorage.removeAllValues(key, inputId);
  }

  private UpdatableValueContainer<Value> getMemValueContainer(final Key key) {
    return myMap.computeIfAbsent(key, k -> {
      return new TransientChangeTrackingValueContainer<>(() -> {
        try {
          return myBackendStorage.read(key);
        }
        catch (StorageException e) {
          throw new RuntimeException(e);
        }
      });
    });
  }

  @Override
  public @NotNull ValueContainer<Value> read(final Key key) throws StorageException {
    final ValueContainer<Value> valueContainer = myMap.get(key);
    if (valueContainer != null) {
      return valueContainer;
    }

    return myBackendStorage.read(key);
  }

  @Override
  public int keysCountApproximately() {
    //RC: this imprecise upper bound -- some keys counted twice since they present in both transient
    //    and persistent storage
    return myMap.size() + myBackendStorage.keysCountApproximately();
  }
}
