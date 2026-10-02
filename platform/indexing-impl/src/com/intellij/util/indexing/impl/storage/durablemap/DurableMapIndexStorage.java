// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage.durablemap;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.platform.util.io.storages.KeyDescriptorEx;
import com.intellij.platform.util.io.storages.StorageFactory;
import com.intellij.platform.util.io.storages.durablemap.DurableMap;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.indexing.IdFilter;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.VfsAwareIndexStorage;
import com.intellij.util.indexing.impl.ChangeTrackingValueContainer;
import com.intellij.util.indexing.impl.IndexStorageLockingBase;
import com.intellij.util.indexing.impl.MapIndexStorageCache;
import com.intellij.util.indexing.impl.MapIndexStorageCacheProvider;
import com.intellij.util.indexing.impl.UpdatableValueContainer;
import com.intellij.util.indexing.impl.ValueContainerImpl;
import com.intellij.util.indexing.impl.ValueContainerInputRemapping;
import com.intellij.util.indexing.impl.ValueContainerProcessor;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.MeasurableIndexStore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

@ApiStatus.Internal
public class DurableMapIndexStorage<Key, Value> extends IndexStorageLockingBase
  implements VfsAwareIndexStorage<Key, Value>, MeasurableIndexStore {
  private static final Logger LOG = Logger.getInstance(DurableMapIndexStorage.class);

  private ValueContainerDurableMap<Key, Value> durableMap;

  private MapIndexStorageCache<Key, Value> cache;

  private final int cacheSize;

  private final KeyDescriptorEx<Key> keyDescriptor;
  //RC: can't move to DataExternalizerEx, because the serializer not used directly, but wrapped into
  //    ValueContainerExternalizer -- and ValueContainerExternalizer implementation doesn't allow
  //    re-implement it easily, and also re-implementation doesn't seem to provide much benefit
  private final DataExternalizer<Value> valueExternalizer;

  /**
   * {@link com.intellij.util.indexing.FileBasedIndexExtension#keyIsUniqueForIndexedFile} and {@link com.intellij.util.indexing.SingleEntryFileBasedIndexExtension},
   * This field is true only then storage created from  {@link com.intellij.util.indexing.SingleEntryFileBasedIndexExtension}
   * with a layout for a single-entry index
   */
  //MAYBE RC: If keyIsUniqueForIndexedFile=true -- it means ValueContainer could contain <=1 entry only.
  //          Why not make it explicit, by implementing SingleEntryValueContainer/SingleEntryChangeTrackingValueContainer?
  //          It seems like the code could be simplified quite a bit -- and also quite a bit of implicit assumptions and
  //          unclear conditions in the code could be made more clear and explicit
  private final boolean keyIsUniqueForIndexedFile;
  private final boolean readOnly;

  private final @NotNull ThrowableComputable<? extends DurableMap<Key, UpdatableValueContainer<Value>>, ? extends IOException> durableMapOpener;

  public DurableMapIndexStorage(@NotNull Path storageFile,
                                @NotNull KeyDescriptorEx<Key> keyDescriptor,
                                @NotNull DataExternalizer<Value> valueExternalizer,
                                int cacheSize,
                                boolean keyIsUniqueForIndexedFile) throws IOException {
    this(
      fileMapOpener(
        storageFile,
        DurableMapIndexStorageFactories.fileBasedMapFactory(keyDescriptor, valueExternalizer, keyIsUniqueForIndexedFile)
      ),
      keyDescriptor,
      valueExternalizer,
      cacheSize,
      keyIsUniqueForIndexedFile,
      true,
      false,
      null
    );
  }

  public DurableMapIndexStorage(@NotNull Path storageFile,
                                @NotNull KeyDescriptorEx<Key> keyDescriptor,
                                @NotNull DataExternalizer<Value> valueExternalizer,
                                int cacheSize,
                                boolean keyIsUniqueForIndexedFile,
                                @NotNull StorageFactory<? extends DurableMap<Key, UpdatableValueContainer<Value>>> durableMapFactory) throws IOException {
    this(fileMapOpener(storageFile, durableMapFactory), keyDescriptor, valueExternalizer, cacheSize, keyIsUniqueForIndexedFile, true, false, null);
  }

  public DurableMapIndexStorage(
    @NotNull ThrowableComputable<? extends DurableMap<Key, UpdatableValueContainer<Value>>, ? extends IOException> durableMapOpener,
    @NotNull KeyDescriptorEx<Key> keyDescriptor,
    @NotNull DataExternalizer<Value> valueExternalizer,
    int cacheSize,
    boolean keyIsUniqueForIndexedFile
  ) throws IOException {
    this(durableMapOpener, keyDescriptor, valueExternalizer, cacheSize, keyIsUniqueForIndexedFile, true, false, null);
  }

  public DurableMapIndexStorage(@NotNull Path storageFile,
                                @NotNull KeyDescriptorEx<Key> keyDescriptor,
                                @NotNull DataExternalizer<Value> valueExternalizer,
                                int cacheSize,
                                boolean keyIsUniqueForIndexedFile,
                                boolean initialize,
                                boolean readOnly,
                                @Nullable ValueContainerInputRemapping inputRemapping) throws IOException {
    this(
      fileMapOpener(
        storageFile,
        DurableMapIndexStorageFactories.fileBasedMapFactory(keyDescriptor, valueExternalizer, keyIsUniqueForIndexedFile, inputRemapping)
      ),
      keyDescriptor,
      valueExternalizer,
      cacheSize,
      keyIsUniqueForIndexedFile,
      initialize,
      readOnly,
      inputRemapping
    );
  }

  private DurableMapIndexStorage(
    @NotNull ThrowableComputable<? extends DurableMap<Key, UpdatableValueContainer<Value>>, ? extends IOException> durableMapOpener,
    @NotNull KeyDescriptorEx<Key> keyDescriptor,
    @NotNull DataExternalizer<Value> valueExternalizer,
    int cacheSize,
    boolean keyIsUniqueForIndexedFile,
    boolean initialize,
    boolean readOnly,
    @Nullable ValueContainerInputRemapping inputRemapping
  ) throws IOException {
    this.keyDescriptor = keyDescriptor;
    this.valueExternalizer = valueExternalizer;

    this.cacheSize = cacheSize;
    this.keyIsUniqueForIndexedFile = keyIsUniqueForIndexedFile;
    this.readOnly = readOnly;

    if (inputRemapping != null) {
      LOG.assertTrue(this.readOnly, "input remapping allowed only for read-only storage");
    }
    this.durableMapOpener = durableMapOpener;

    if (initialize) {
      initMapAndCache();
    }
  }

  protected void initMapAndCache() throws IOException {
    withWriteLock(() -> {
      var map = createValueContainerMap();
      try {
        cache = MapIndexStorageCacheProvider.Companion.getActualProvider().createCache(
          map::getModifiableValueContainer,
          this::onDropFromCache,
          keyDescriptor,
          cacheSize
        );
        durableMap = map;
      }
      catch (RuntimeException | Error e) {
        try {
          map.close();
        }
        catch (IOException closeError) {
          e.addSuppressed(closeError);
        }
        throw e;
      }
    });
  }

  private void onDropFromCache(Key key,
                               @NotNull ChangeTrackingValueContainer<Value> valueContainer) {
    //This method needs a _read_ lock acquired up the stack, because:
    // a) DurableMap has its own thread-safety measures, i.e. methods .remove()/.merge() don't need external synchronization
    // b) ValueContainer is used in read-only fashion
    // c) ...except for ValueContainer.setNeedsCompacting(true), which relies on .needsCompacting being volatile
    try {
      if (readOnly || !valueContainer.isDirty()) {
        return;
      }

      if (keyIsUniqueForIndexedFile) {
        if (valueContainer.containsOnlyInvalidatedChange()) {
          durableMap.remove(key);
          return;
        }

        //RC: afaicu, this is done just to ensure we do NOT use append-changes branch in a .merge().
        //    Append-changes is useless in keyIsUniqueForFile case because there is always <=1 (inputId, value) entry in
        //    ValueContainer, and at this point container could contain only 1 update change that container has changes (isDirty) and those changes are not removals
        //    (!containsOnlyInvalidatedChange) which (for keyIsUniqueForFile) implies there is 1 and only 1 added change
        if (valueContainer.containsCachedMergedData()) {
          //FIXME RC: but for setNeedsCompacting() we need a write lock! -- or this is why needsCompacting is volatile?
          valueContainer.setNeedsCompacting(true);
        }
      }
      durableMap.merge(key, valueContainer);
    }
    catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private @NotNull ValueContainerDurableMap<Key, Value> createValueContainerMap() throws IOException {
    DurableMap<Key, UpdatableValueContainer<Value>> durableMap = durableMapOpener.compute();
    return new ValueContainerDurableMap<>(durableMap, valueExternalizer, keyIsUniqueForIndexedFile);
  }

  @Override
  public void updateValue(@NotNull Key key,
                          int inputId,
                          Value newValue) throws StorageException {
    if (readOnly) {
      throw new IncorrectOperationException("Index storage is read-only");
    }
    withWriteLock(() -> {
      try {
        if (keyIsUniqueForIndexedFile) {
          assertKeyInputIdConsistency(key, inputId);
          updateSingleValueDirectly(key, inputId, newValue);
        }
        else {
          removeAllValues(key, inputId);
          addValue(key, inputId, newValue);
        }
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
    });
  }

  @Override
  public void addValue(@NotNull Key key,
                       int inputId,
                       Value value) throws StorageException {
    if (readOnly) {
      throw new IncorrectOperationException("Index storage is read-only");
    }
    withWriteLock(() -> {
      try {
        if (keyIsUniqueForIndexedFile) {
          assertKeyInputIdConsistency(key, inputId);
          putSingleValueDirectly(key, inputId, value);
        }
        else {
          ChangeTrackingValueContainer<Value> container = cache.read(key);
          container.addValue(inputId, value);
        }
      }
      catch (RuntimeException e) {
        throw unwrapCauseAndRethrow(e);
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
    });
  }

  @Override
  public void removeAllValues(@NotNull Key key,
                              int inputId) throws StorageException {
    if (readOnly) {
      throw new IncorrectOperationException("Index storage is read-only");
    }
    withWriteLock(() -> {
      try {
        if (keyIsUniqueForIndexedFile) {
          assertKeyInputIdConsistency(key, inputId);
          removeSingleValueDirectly(key, inputId);
        }
        else {
          // important: assuming the key exists in the index
          ChangeTrackingValueContainer<Value> container = cache.read(key);
          container.removeAssociatedValue(inputId);
        }
      }
      catch (RuntimeException e) {
        throw unwrapCauseAndRethrow(e);
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
    });
  }

  @Override
  public void flush() throws IOException {
    withWriteLock(() -> {
      if (!durableMap.isClosed()) {
        invalidateCachedMappings();
        if (durableMap.isDirty()) durableMap.force();
      }
    });
  }

  @Override
  public boolean isDirty() {
    if (durableMap.isDirty()) {
      return true;
    }

    return withReadLock(() -> {
      for (ChangeTrackingValueContainer<Value> container : cache.getCachedValues()) {
        if (container.isDirty()) {
          return true;
        }
      }
      return false;
    });
  }

  @Override
  public int keysCountApproximately() {
    return durableMap.keysCountApproximately();
  }

  @Override
  public void close() throws IOException {
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class, IOException::new,

      this::flush,
      durableMap::close
    );
  }

  @Override
  public boolean isClosed() {
    return durableMap.isClosed();
  }

  @Override
  public void clear() throws StorageException {
    withWriteLock(() -> {
      try {
        durableMap.closeAndClean();
      }
      catch (Exception ignored) {
      }

      try {
        initMapAndCache();
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
      catch (RuntimeException e) {
        unwrapCauseAndRethrow(e);
      }
    });
  }

  @Override
  public <E extends Exception> boolean read(Key key,
                                            @NotNull ValueContainerProcessor<Value, E> processor) throws StorageException, E {
    try (LockStamp ignored = lockForRead()) {
      try {
        ChangeTrackingValueContainer<Value> container = cache.read(key);
        return processor.process(container);
      }
      catch (RuntimeException e) {
        throw unwrapCauseAndRethrow(e);
      }
    }
  }

  /**
   * removes (key, inputId) tuple from index: special case there inputId is mapped to a single value.
   * We use artificial (key=inputId) for such case (see SingleEntryIndexer) so in that case key is
   * unique: only single inputId could have key(=inputId) => removing that key means there could be
   * no other (inputId,value) linked to it
   */
  private void removeSingleValueDirectly(Key key, int inputId) throws IOException {
    assert keyIsUniqueForIndexedFile;
    ChangeTrackingValueContainer<Value> cached = readIfCached(key);

    if (cached != null) {
      cached.removeAssociatedValue(inputId);
      return;
    }

    durableMap.remove(key);
  }

  private void updateSingleValueDirectly(Key key, int inputId, Value newValue) throws IOException {
    assert keyIsUniqueForIndexedFile;

    ChangeTrackingValueContainer<Value> cached = readIfCached(key);
    if (cached != null) {
      cached.removeAssociatedValue(inputId);
      cached.addValue(inputId, newValue);
      return;
    }

    // do not pollute the cache with keys unique to indexed file
    UpdatableValueContainer<Value> valueContainer = ValueContainerImpl.createNewValueContainer();
    valueContainer.addValue(inputId, newValue);
    durableMap.put(key, valueContainer);
  }

  private void putSingleValueDirectly(Key key, int inputId, Value value) throws IOException {
    assert keyIsUniqueForIndexedFile;

    ChangeTrackingValueContainer<Value> cached = readIfCached(key);
    if (cached != null) {
      cached.addValue(inputId, value);
      return;
    }

    // do not pollute the cache with keys unique to indexed file
    UpdatableValueContainer<Value> valueContainer = ValueContainerImpl.createNewValueContainer();
    valueContainer.addValue(inputId, value);
    durableMap.put(key, valueContainer);
  }

  //requires at least read lock
  private @Nullable ChangeTrackingValueContainer<Value> readIfCached(Key key) {
    return cache.readIfCached(key);
  }

  @Override
  public void clearCaches() {
    //RC: strictly speaking we don't need a lock here, since .dropMergedData() uses volatile -- but I don't like to
    //    rely on such a fine implementation detail
    withWriteLock(() -> {
      for (ChangeTrackingValueContainer<Value> container : cache.getCachedValues()) {
        container.dropMergedData();
      }
    });
  }

  @Override
  @ApiStatus.Internal
  public final void invalidateCachedMappings() {
    // It is enough to have readLock for flushing the data because
    // 1) underlying storage has its own lock, and
    // 2) ValueContainers are only read-accessed during flushing
    withReadLock(() -> cache.invalidateAll());
  }

  @Override
  public boolean processKeys(@NotNull Processor<? super Key> processor,
                             @NotNull GlobalSearchScope scope,
                             @Nullable IdFilter idFilter) throws StorageException {
    return processKeys(processor);
  }

  private boolean processKeys(@NotNull Processor<? super Key> processor) throws StorageException {
    return withReadLock(() -> {
      try {
        invalidateCachedMappings(); // this will ensure that all new keys are made into the map
        return durableMap.processKeys(processor);
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
      catch (RuntimeException e) {
        throw unwrapCauseAndRethrow(e);
      }
    });
  }

  public static @NotNull Path getIndexStorageFile(@NotNull Path baseFile) {
    return baseFile.resolveSibling(baseFile.getFileName() + ".storage");
  }

  private static <Key, Value> @NotNull ThrowableComputable<DurableMap<Key, UpdatableValueContainer<Value>>, IOException> fileMapOpener(
    @NotNull Path storageFile,
    @NotNull StorageFactory<? extends DurableMap<Key, UpdatableValueContainer<Value>>> durableMapFactory
  ) {
    Path indexStorageFile = getIndexStorageFile(storageFile);
    return () -> durableMapFactory.open(indexStorageFile);
  }

  private static void assertKeyInputIdConsistency(@NotNull Object key, int inputId) {
    assert ((Integer)key).intValue() == inputId;
  }

  @Contract("_ -> fail")
  private static StorageException unwrapCauseAndRethrow(RuntimeException e) throws StorageException {
    Throwable cause = e.getCause();
    if (cause instanceof IOException) {
      throw new StorageException(cause);
    }
    if (cause instanceof StorageException) {
      throw (StorageException)cause;
    }
    throw e;
  }
}
