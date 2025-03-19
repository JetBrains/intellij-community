// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.indexing.impl.storage;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.*;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This storage is needed for indexing yet unsaved data without saving those changes to 'main' backend storage
 * <p>
 * Data is stored _either_ in-memory, or in underlyingStorage, but not both: if a key has it's data in inMemoryStorage,
 * this data completely shadows same key's data in underlyingStorage.
 */
@Internal
public final class TransientChangesIndexStorage<Key, Value> implements VfsAwareIndexStorage<Key, Value> {
  private static final Logger LOG = Logger.getInstance(TransientChangesIndexStorage.class);

  /** Used for debug/logging */
  private final @NotNull ID<?, ?> indexId;

  private final Map<Key, TransientChangeTrackingValueContainer<Value>> inMemoryStorage;
  private final @NotNull VfsAwareIndexStorage<Key, Value> underlyingStorage;

  /**
   * If buffering is enabled, updates are accumulated only in inMemoryStorage.
   * Otherwise, updates go both inMemoryStorage and underlyingStorage.
   * MAYBE RC: 'mode switching' is not the best approach here, given all the thread-safety concerns.
   *           Ideally, 'buffering' should be a property of each update but currently we can't add a new param to
   *           update methods, since they are defined by IndexStorage iface
   */
  private boolean bufferingEnabled;
  private final List<BufferingStateListener> bufferingStateListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public interface BufferingStateListener {
    void bufferingStateChanged(boolean newState);

    void memoryStorageCleared();
  }

  public TransientChangesIndexStorage(@NotNull IndexStorage<Key, Value> underlyingStorage,
                                      @NotNull FileBasedIndexExtension<Key, Value> extension) {
    this.underlyingStorage = (VfsAwareIndexStorage<Key, Value>)underlyingStorage;
    indexId = extension.getName();
    inMemoryStorage = ConcurrentCollectionFactory.createConcurrentMap(IndexStorageUtil.adaptKeyDescriptorToStrategy(extension.getKeyDescriptor()));
  }

  public void addBufferingStateListener(@NotNull BufferingStateListener listener) {
    bufferingStateListeners.add(listener);
  }

  public void setBufferingEnabled(boolean enabled) {
    boolean wasEnabled = bufferingEnabled;
    LOG.assertTrue(wasEnabled != enabled);

    bufferingEnabled = enabled;
    for (BufferingStateListener listener : bufferingStateListeners) {
      listener.bufferingStateChanged(enabled);
    }
  }

  public boolean clearMemoryMap() {
    boolean modified = !inMemoryStorage.isEmpty();
    inMemoryStorage.clear();
    if (modified && FileBasedIndexEx.doTraceStubUpdates(indexId)) {
      LOG.info("clearMemoryMap,index=" + indexId);
    }
    return modified;
  }

  public boolean clearMemoryMapForId(Key key, int fileId) {
    TransientChangeTrackingValueContainer<Value> container = inMemoryStorage.get(key);
    if (container != null) {
      container.dropAssociatedValue(fileId);
      if (FileBasedIndexEx.doTraceStubUpdates(indexId)) {
        LOG.info("clearMemoryMapForId,inputId=" + fileId + ",index=" + indexId + ",key=" + key);
      }
      return true;
    }
    return false;
  }

  public void fireMemoryStorageCleared() {
    for (BufferingStateListener listener : bufferingStateListeners) {
      listener.memoryStorageCleared();
    }
  }

  @Override
  public void clearCaches() {
    try {
      if (inMemoryStorage.isEmpty()) return;

      if (IndexDebugProperties.DEBUG || FileBasedIndexEx.doTraceStubUpdates(indexId)) {
        LOG.info("clearCaches,index=" + indexId + ",number of items:" + inMemoryStorage.size());
      }

      for (ChangeTrackingValueContainer<Value> v : inMemoryStorage.values()) {
        v.dropMergedData();
      }
    }
    finally {
      underlyingStorage.clearCaches();
    }
  }

  @Override
  public void close() throws IOException {
    underlyingStorage.close();
  }

  @Override
  @Internal
  public boolean isClosed() {
    return underlyingStorage.isClosed();
  }

  @Override
  public void clear() throws StorageException {
    clearMemoryMap();
    underlyingStorage.clear();
  }

  @Override
  public void flush() throws IOException {
    underlyingStorage.flush();
  }

  @Override
  public boolean isDirty() {
    return underlyingStorage.isDirty();
  }

  @Override
  public boolean processKeys(@NotNull Processor<? super Key> processor, @NotNull GlobalSearchScope scope, IdFilter idFilter)
    throws StorageException {
    Set<Key> stopList = new HashSet<>();

    Processor<Key> decoratingProcessor = key -> {
      if (stopList.contains(key)) return true;

      UpdatableValueContainer<Value> container = inMemoryStorage.get(key);
      if (container != null && container.size() == 0) {
        return true;
      }
      return processor.process(key);
    };

    for (Key key : inMemoryStorage.keySet()) {
      if (!decoratingProcessor.process(key)) {
        return false;
      }
      stopList.add(key);
    }
    return underlyingStorage.processKeys(stopList.isEmpty() ? processor : decoratingProcessor, scope, idFilter);
  }


  @Override
  public void updateValue(Key key, int inputId, Value newValue) throws StorageException {
    if (bufferingEnabled) {
      UpdatableValueContainer<Value> memContainer = getMemValueContainer(key);
      memContainer.removeAssociatedValue(inputId);
      memContainer.addValue(inputId, newValue);
      return;
    }

    ChangeTrackingValueContainer<Value> valueContainer = inMemoryStorage.get(key);
    if (valueContainer != null) {
      valueContainer.dropMergedData();
    }

    underlyingStorage.updateValue(key, inputId, newValue);
  }

  @Override
  public void addValue(Key key, int inputId, Value value) throws StorageException {
    if (FileBasedIndexEx.doTraceStubUpdates(indexId)) {
      LOG.info("addValue,inputId=" + inputId + ",index=" + indexId + ",inMemory=" + bufferingEnabled + "," + value);
    }

    if (bufferingEnabled) {
      getMemValueContainer(key).addValue(inputId, value);
      return;
    }
    ChangeTrackingValueContainer<Value> valueContainer = inMemoryStorage.get(key);
    if (valueContainer != null) {
      valueContainer.dropMergedData();
    }

    underlyingStorage.addValue(key, inputId, value);
  }

  @Override
  public void removeAllValues(@NotNull Key key, int inputId) throws StorageException {
    if (FileBasedIndexEx.doTraceStubUpdates(indexId)) {
      LOG.info("removeAllValues,inputId=" + inputId + ",index=" + indexId + ",inMemory=" + bufferingEnabled);
    }

    if (bufferingEnabled) {
      getMemValueContainer(key).removeAssociatedValue(inputId);
      return;
    }
    ChangeTrackingValueContainer<Value> valueContainer = inMemoryStorage.get(key);
    if (valueContainer != null) {
      valueContainer.dropMergedData();
    }

    underlyingStorage.removeAllValues(key, inputId);
  }

  private UpdatableValueContainer<Value> getMemValueContainer(Key key) {
    return inMemoryStorage.computeIfAbsent(key, k -> new TransientChangeTrackingValueContainer<>(() -> {
      try {
        return (UpdatableValueContainer<Value>)underlyingStorage.read(key);
      }
      catch (StorageException e) {
        throw new RuntimeException(e);
      }
    }));
  }

  @Override
  public <E extends Exception> boolean read(Key key,
                                            @NotNull ValueContainerProcessor<Value, E> processor) throws StorageException, E {
    ValueContainer<Value> valueContainer = inMemoryStorage.get(key);
    if (valueContainer != null) {
      return processor.process(valueContainer);
    }

    return underlyingStorage.read(key, processor);
  }

  @Override
  public int keysCountApproximately() {
    //RC: this is an imprecise upper bound -- some keys counted twice since they present in both transient
    //    and persistent storage
    return inMemoryStorage.size() + underlyingStorage.keysCountApproximately();
  }
}
