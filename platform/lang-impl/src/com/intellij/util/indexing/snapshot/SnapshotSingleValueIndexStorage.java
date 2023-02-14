// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.snapshot;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.ValueContainerImpl;
import com.intellij.util.indexing.impl.forward.IntForwardIndex;
import com.intellij.util.indexing.storage.UpdatableSnapshotInputMappingIndex;
import com.intellij.util.io.MeasurableIndexStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@link VfsAwareIndexStorage} implementation for {@link SingleEntryFileBasedIndexExtension} indexes.
 */
public class SnapshotSingleValueIndexStorage<Key, Value> implements VfsAwareIndexStorage<Key, Value> {
  private static final Logger LOG = Logger.getInstance(SnapshotSingleValueIndexStorage.class);

  // shareable snapshots
  private volatile UpdatableSnapshotInputMappingIndex<Key, Value, FileContent> mySnapshotInputMappings;

  // input -> hash (client instance dependent)
  private volatile IntForwardIndex myForwardIndex;

  private final Lock myCacheLock = new ReentrantLock();
  private final SLRUCache<Key, ValueContainer<Value>> myCache;

  public SnapshotSingleValueIndexStorage(int cacheSize) {
    myCache = new SLRUCache<>(cacheSize, (int)(Math.ceil(cacheSize * 0.25))) {
      @NotNull
      @Override
      public ValueContainer<Value> createValue(Key key) {
        try {
          return doRead(key);
        }
        catch (StorageException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  private volatile boolean myInitialized;

  public void init(@NotNull SnapshotInputMappings<Key, Value> snapshotInputMappings, @NotNull IntForwardIndex forwardIndex) {
    assert !myInitialized;
    myForwardIndex = forwardIndex;
    mySnapshotInputMappings = snapshotInputMappings;
    myInitialized = true;
  }

  @NotNull
  @Override
  public ValueContainer<Value> read(Key key) throws StorageException {
    assert myInitialized;
    myCacheLock.lock();
    try {
      return myCache.get(key);
    } finally {
      myCacheLock.unlock();
    }
  }

  @NotNull
  private ValueContainer<Value> doRead(Key key) throws StorageException {
    int inputId = inputKey(key);
    try {
      int hashId = myForwardIndex.getInt(inputId);
      if (hashId == 0) return new ValueContainerImpl<>();
      // we have mapped hash, so we have a record
      Value item = ContainerUtil.getFirstItem(mySnapshotInputMappings.readData(hashId).values());
      ValueContainerImpl<Value> container = new ValueContainerImpl<>();
      container.addValue(inputId, item);
      return container;
    }
    catch (IOException e) {
      throw new StorageException(e);
    }
  }


  @Override
  public boolean processKeys(@NotNull Processor<? super Key> processor, GlobalSearchScope scope, @Nullable IdFilter idFilter) {
    assert myInitialized;
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void addValue(Key key, int inputId, Value value) {
    assert myInitialized;
    checkKeyInputIdConsistency(key, inputId);
    myCache.remove(key);
    // do nothing, all data update is served by forward index, the only one we need to serve here is read access
  }

  @Override
  public void removeAllValues(@NotNull Key key, int inputId) {
    assert myInitialized;
    checkKeyInputIdConsistency(key, inputId);
    myCache.remove(key);
    // do nothing, all data update is served by forward index, the only one we need to serve here is read access
  }

  @Override
  public void clear() {
    clearCaches();
  }

  @Override
  public void clearCaches() {
    myCacheLock.lock();
    try {
      myCache.clear();
    } finally {
      myCacheLock.unlock();
    }
  }

  @Override
  public int keysCountApproximately() {
    assert myInitialized;
    return MeasurableIndexStore.keysCountApproximatelyIfPossible(myForwardIndex);
  }

  @Override
  public void close() throws StorageException {}

  @Override
  public void flush() throws IOException {
    clearCaches();
  }

  private void checkKeyInputIdConsistency(Key key, int inputId) {
    if (!Comparing.equal(key, inputId)) {
      LOG.error("key (" + key + ") and inputId (" + inputId + ") should be the same for " + SingleEntryFileBasedIndexExtension.class.getName());
    }
  }

  private static <Key> int inputKey(Key key) {
    return (int)(Integer)key;
  }

  @SuppressWarnings("unchecked")
  public static <Value> ValueContainer<Value> empty() {
    return EmptyValueContainer.INSTANCE;
  }
}
