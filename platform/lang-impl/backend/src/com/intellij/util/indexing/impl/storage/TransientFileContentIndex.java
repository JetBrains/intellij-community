// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.indexing.FileBasedIndexEx;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.impl.DirectInputDataDiffBuilder;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.forward.AbstractMapForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.IterableForwardIndex;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntPredicate;

import static com.intellij.concurrency.ConcurrentCollectionFactory.createConcurrentIntObjectMap;

/** In-memory index, with persistent index as a 'backend' storage -- so it is not really 'transient' */
@Internal
public class TransientFileContentIndex<Key, Value, FileCachedData extends VfsAwareMapReduceIndex.IndexerIdHolder>
  extends VfsAwareMapReduceIndex<Key, Value, FileCachedData> {
  private static final Logger LOG = Logger.getInstance(TransientFileContentIndex.class);

  private final AtomicBoolean myInMemoryMode = new AtomicBoolean();
  private final ConcurrentIntObjectMap<Map<Key, Value>> myInMemoryKeysAndValues;


  public TransientFileContentIndex(@NotNull FileBasedIndexExtension<Key, Value> extension,
                                   @NotNull VfsAwareIndexStorageLayout<Key, Value> indexStorageLayout) throws IOException {
    this(extension, indexStorageLayout, createConcurrentIntObjectMap());
  }

  //this ctor is just a trick to share .myInMemoryKeysAndValues ref between this class, and TransientIterableForwardIndex
  // that is created during super() call
  private TransientFileContentIndex(@NotNull FileBasedIndexExtension<Key, Value> extension,
                                    @NotNull VfsAwareIndexStorageLayout<Key, Value> indexStorageLayout,
                                    @NotNull ConcurrentIntObjectMap<Map<Key, Value>> transientData) throws IOException {
    super(extension,
          () -> new TransientChangesIndexStorage<>(indexStorageLayout.openIndexStorage(), extension),
          () -> {
            ForwardIndex index = indexStorageLayout.openForwardIndex();
            if (index instanceof IterableForwardIndex iterableIndex) {
              return new TransientIterableForwardIndex<>(iterableIndex, transientData);
            }
            return index;
          },
          indexStorageLayout.getForwardIndexAccessor()
    );

    myInMemoryKeysAndValues = transientData;
    installMemoryModeListener();
  }

  @Override
  protected @NotNull InputDataDiffBuilder<Key, Value> getKeysDiffBuilder(int inputId) throws IOException {
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
    if (FileBasedIndexEx.doTraceStubUpdates(indexId())) {
      LOG.info("updateForwardIndex,inputId=" + indexId() + ",index=" + indexId() + ",inMemory=" + myInMemoryMode.get());
    }

    if (myInMemoryMode.get()) {
      myInMemoryKeysAndValues.put(inputId, data.getKeyValues());
    }
    else {
      super.updateForwardIndex(inputId, data);
    }
  }

  @Override
  protected @Nullable Map<Key, Value> getNullableIndexedData(int fileId) throws IOException, StorageException {
    if (myInMemoryMode.get()) {
      Map<Key, Value> map = myInMemoryKeysAndValues.get(fileId);
      if (map != null) return map;
    }
    return super.getNullableIndexedData(fileId);
  }

  private void installMemoryModeListener() {
    IndexStorage<Key, Value> storage = getStorage();
    if (storage instanceof TransientChangesIndexStorage) {
      ((TransientChangesIndexStorage<Key, Value>)storage).addBufferingStateListener(
        new TransientChangesIndexStorage.BufferingStateListener() {
          @Override
          public void bufferingStateChanged(boolean newState) {
            myInMemoryMode.set(newState);
          }

          @Override
          public void memoryStorageCleared() {
            if (FileBasedIndexEx.doTraceStubUpdates(indexId())) {
              LOG.info("memoryStorageCleared,index=" + indexId());
            }
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
      LOG.assertTrue(ProgressManager.getInstance().isInNonCancelableSection(), "Must be called in a nonCancellableSection");
    }
    //TODO RC: do we need a lock around here?
    if (FileBasedIndexEx.doTraceStubUpdates(indexId())) {
      LOG.info("removeTransientDataForFile,inputId=" + inputId + ",index=" + indexId());
    }
    Map<Key, Value> keyValueMap = myInMemoryKeysAndValues.remove(inputId);
    if (keyValueMap == null) return;

    try {
      removeTransientDataForInMemoryKeys(inputId, keyValueMap);
      InputDataDiffBuilder<Key, Value> builder = getKeysDiffBuilder(inputId);
      removeTransientDataForKeys(inputId, builder);
    }
    catch (IOException ex) {
      throw new UncheckedIOException(ex);
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
    //TODO RC: Other modifications (e.g.removeTransientDataForFile) are protected with writeLock?
    memStorage.clearCaches();
  }

  public static <Key, Value> TransientFileContentIndex<Key, Value, VfsAwareMapReduceIndex.IndexerIdHolder> createIndex(@NotNull FileBasedIndexExtension<Key, Value> extension,
                                                                                                                       @NotNull VfsAwareIndexStorageLayout<Key, Value> indexStorageLayout)
    throws IOException {
    return new TransientFileContentIndex<>(extension, indexStorageLayout);
  }

  /** Uses transient file data instead of persistent data during key traversal */
  private static final class TransientIterableForwardIndex<Key, Value> implements IterableForwardIndex {

    private final IterableForwardIndex delegate;

    private final ConcurrentIntObjectMap<Map<Key, Value>> transientData;

    private TransientIterableForwardIndex(@NotNull IterableForwardIndex delegate,
                                          @NotNull ConcurrentIntObjectMap<Map<Key, Value>> transientData) {
      this.delegate = delegate;
      this.transientData = transientData;
    }

    @Override
    public @Nullable ByteArraySequence get(@NotNull Integer fileId) throws IOException {
      return delegate.get(fileId);
    }

    @Override
    public void put(@NotNull Integer fileId, @Nullable ByteArraySequence data) throws IOException {
      delegate.put(fileId, data);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K> boolean processFilteredKeys(@NotNull IntPredicate inputIdFilter,
                                           @NotNull AbstractMapForwardIndexAccessor<K, ?, ?> accessor,
                                           @NotNull Processor<? super K> processor) throws IOException {
      boolean allKeysProcessed = delegate.processFilteredKeys(
        fileId -> inputIdFilter.test(fileId) && !transientData.containsKey(fileId),
        accessor,
        processor
      );
      if (!allKeysProcessed) return false;

      for (int fileId : transientData.keys()) {
        if (!inputIdFilter.test(fileId)) continue;
        Map<Key, Value> data = transientData.get(fileId);
        if (data != null) {
          for (Key key : data.keySet()) {
            if (!processor.process((K)key)) return false;
          }
        }
      }
      return true;
    }

    @Override
    public void clear() throws IOException {
      try {
        delegate.clear();
      }
      finally {
        transientData.clear();
      }
    }

    @Override
    public boolean isClosed() {
      return delegate.isClosed();
    }

    @Override
    public void force() throws IOException {
      delegate.force();
    }

    @Override
    public boolean isDirty() {
      return delegate.isDirty();
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }
}
