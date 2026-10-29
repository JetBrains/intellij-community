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
import com.intellij.util.indexing.impl.storage.KeyHashLog;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.MeasurableIndexStore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.IntPredicate;

import static java.util.Objects.requireNonNull;

@ApiStatus.Internal
public class DurableMapIndexStorage<Key, Value> extends IndexStorageLockingBase
  implements VfsAwareIndexStorage<Key, Value>, MeasurableIndexStore {
  private static final Logger LOG = Logger.getInstance(DurableMapIndexStorage.class);

  private ValueContainerDurableMap<Key, Value> durableMap;

  private MapIndexStorageCache<Key, Value> cache;
  private final int cacheSize;

  private final @Nullable KeyHashLogAccessor<Key> keyHashLogAccessor;
  private @Nullable KeyHashLog<Key> keyHashLog;
  /// True if the log's data is consistent with main storage.
  /// Set false on any error, and then [#processKeys] fallbacks to full-scan.
  /// Also, the false value is used in [#close] to clean the invalid data.
  private volatile boolean keyHashLogValid;
  /// Supports [#isDirty]
  private volatile boolean keyHashLogDirty;

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
  private final boolean traceKeyHashToVirtualFileMapping;
  private final boolean readOnly;

  private final @NotNull ThrowableComputable<? extends DurableMap<Key, UpdatableValueContainer<Value>>, ? extends IOException> durableMapOpener;

  public DurableMapIndexStorage(@NotNull Path storageFile,
                                @NotNull KeyDescriptorEx<Key> keyDescriptor,
                                @NotNull DataExternalizer<Value> valueExternalizer,
                                int cacheSize,
                                boolean keyIsUniqueForIndexedFile,
                                boolean traceKeyHashToVirtualFileMapping,
                                @NotNull KeyHashLogAccessor<Key> keyHashLogAccessor) throws IOException {
    this(
      fileMapOpener(
        storageFile,
        DurableMapIndexStorageFactories.fileBasedMapFactory(keyDescriptor, valueExternalizer, keyIsUniqueForIndexedFile)
      ),
      keyDescriptor,
      valueExternalizer,
      cacheSize,
      keyIsUniqueForIndexedFile,
      traceKeyHashToVirtualFileMapping, keyHashLogAccessor,
      /* initialize: */ true,
      /* readOnly:   */ false,
      null
    );
  }

  public DurableMapIndexStorage(@NotNull Path storageFile,
                                @NotNull KeyDescriptorEx<Key> keyDescriptor,
                                @NotNull DataExternalizer<Value> valueExternalizer,
                                int cacheSize,
                                boolean keyIsUniqueForIndexedFile,
                                boolean traceKeyHashToVirtualFileMapping,
                                @NotNull KeyHashLogAccessor<Key> keyHashLogAccessor,
                                @NotNull StorageFactory<? extends DurableMap<Key, UpdatableValueContainer<Value>>> durableMapFactory
  ) throws IOException {
    this(
      fileMapOpener(storageFile, durableMapFactory), keyDescriptor, valueExternalizer, cacheSize,
      keyIsUniqueForIndexedFile,
      traceKeyHashToVirtualFileMapping, keyHashLogAccessor,
      /*initialize: */ true, /*readOnly: */ false, /*inputRemapping: */ null
    );
  }

  public DurableMapIndexStorage(
    @NotNull ThrowableComputable<? extends DurableMap<Key, UpdatableValueContainer<Value>>, ? extends IOException> durableMapOpener,
    @NotNull KeyDescriptorEx<Key> keyDescriptor,
    @NotNull DataExternalizer<Value> valueExternalizer,
    int cacheSize,
    boolean keyIsUniqueForIndexedFile,
    boolean traceKeyHashToVirtualFileMapping,
    @NotNull KeyHashLogAccessor<Key> keyHashLogAccessor
  ) throws IOException {
    this(
      durableMapOpener,
      keyDescriptor,
      valueExternalizer,
      cacheSize,
      keyIsUniqueForIndexedFile,
      traceKeyHashToVirtualFileMapping, keyHashLogAccessor,
      /*initialize: */ true, /*readOnly: */ false, /*inputRemapping: */ null
    );
  }

  public DurableMapIndexStorage(@NotNull Path storageFile,
                                @NotNull KeyDescriptorEx<Key> keyDescriptor,
                                @NotNull DataExternalizer<Value> valueExternalizer,
                                int cacheSize,
                                boolean keyIsUniqueForIndexedFile,
                                boolean traceKeyHashToVirtualFileMapping,
                                @Nullable("Null for read-only storages") KeyHashLogAccessor<Key> keyHashLogAccessor,
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
      traceKeyHashToVirtualFileMapping, keyHashLogAccessor,
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
    boolean traceKeyHashToVirtualFileMapping,
    @Nullable("Null for read-only storages") KeyHashLogAccessor<Key> keyHashLogAccessor,
    boolean initialize,
    boolean readOnly,
    @Nullable ValueContainerInputRemapping inputRemapping
  ) throws IOException {
    this.keyDescriptor = keyDescriptor;
    this.valueExternalizer = valueExternalizer;

    this.cacheSize = cacheSize;
    this.keyIsUniqueForIndexedFile = keyIsUniqueForIndexedFile;
    this.readOnly = readOnly;

    this.traceKeyHashToVirtualFileMapping = traceKeyHashToVirtualFileMapping;
    // keyHashLogAccessor is needed even with !traceKeyHashToVirtualFileMapping to ensure (possible) key-hash-log from previous
    // session (with traceKeyHashToVirtualFileMapping=true) is cleaned:
    this.keyHashLogAccessor = readOnly ? null : requireNonNull(keyHashLogAccessor);

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

        keyHashLogValid = false;
        if (keyHashLogAccessor != null) {
          if (traceKeyHashToVirtualFileMapping) {
            var log = keyHashLogAccessor.open();
            keyHashLog = log;
            if (keyHashLogAccessor.requiresRebuild()) {
              rebuildKeyHashLog(map, log);
            }
            keyHashLogValid = true;
          }
          else {
            // Later sessions must rebuild the journal after writes without tracing.
            keyHashLogAccessor.cleanData();
          }
          keyHashLogDirty = false;
        }

      }
      catch (IOException | RuntimeException | Error e) {
        try {
          ExceptionUtil.runAllAndRethrowAllExceptions(
            IOException.class, IOException::new,
            map::close,
            () -> { if (keyHashLogAccessor != null) keyHashLogAccessor.close(/*clean: */false); }
          );
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
    catch (RuntimeException | Error e) {
      keyHashLogValid = false;
      throw e;
    }
    catch (IOException e) {
      keyHashLogValid = false;
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
          recordKeyHash(key, inputId);
          updateSingleValueDirectly(key, inputId, newValue);
        }
        else {
          removeAllValues(key, inputId);
          addValue(key, inputId, newValue);
        }
      }
      catch (StorageException e) {
        keyHashLogValid = false;
        throw e;
      }
      catch (RuntimeException e) {
        keyHashLogValid = false;
        throw unwrapCauseAndRethrow(e);
      }
      catch (IOException e) {
        keyHashLogValid = false;
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
        recordKeyHash(key, inputId);
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
        keyHashLogValid = false;
        throw unwrapCauseAndRethrow(e);
      }
      catch (IOException e) {
        keyHashLogValid = false;
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
        recordKeyHash(key, -inputId);
      }
      catch (RuntimeException e) {
        keyHashLogValid = false;
        throw unwrapCauseAndRethrow(e);
      }
      catch (IOException e) {
        keyHashLogValid = false;
        throw new StorageException(e);
      }
    });
  }

  @Override
  public void flush() throws IOException {
    withWriteLock(() -> {
      try {
        if (!durableMap.isClosed()) {
          invalidateCachedMappings();
          if (durableMap.isDirty()) durableMap.force();
          if (keyHashLog != null) keyHashLog.flush();
          keyHashLogDirty = false;
        }
      }
      catch (IOException | RuntimeException | Error e) {
        keyHashLogValid = false;
        throw e;
      }
    });
  }

  @Override
  public boolean isDirty() {
    if (keyHashLogDirty || durableMap.isDirty()) {
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
    // The caller must ensure that no other storage operations run concurrently with close()
    boolean[] clean = {keyHashLogValid};
    ExceptionUtil.runAllAndRethrowAllExceptions(
      IOException.class, IOException::new,
      () -> {
        try {
          flush();
        }
        catch (IOException | RuntimeException | Error e) {
          clean[0] = false;
          throw e;
        }
      },
      () -> {
        try {
          durableMap.close();
        }
        catch (IOException | RuntimeException | Error e) {
          clean[0] = false;
          throw e;
        }
      },
      () -> { if (keyHashLogAccessor != null) keyHashLogAccessor.close(clean[0]); }
    );
  }

  @Override
  public boolean isClosed() {
    return durableMap.isClosed();
  }

  @Override
  public void clear() throws StorageException {
    withWriteLock(() -> {
      keyHashLogValid = false;
      try {
        if (keyHashLogAccessor != null) {
          keyHashLogAccessor.close(false);
          keyHashLogAccessor.cleanData();
          keyHashLog = null;
        }
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
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
    if (!traceKeyHashToVirtualFileMapping || idFilter == null || idFilter == IdFilter.ACCEPT_ALL) {
      //if scope is not defined, or storage is not configured to support the scoping -- supply all the keys
      return processKeys(processor);
    }

    return withReadLock(() -> {
      try {
        invalidateCachedMappings(); // this will ensure that all new keys are made into the map

        if (keyHashLogValid && keyHashLog != null) {
          var project = scope.getProject();
          IntPredicate hashes;
          try {
            hashes = project == null ? keyHashLog.getSuitableKeyHashes(idFilter)::contains
                                     : keyHashLog.getSuitableKeyHashes(idFilter, project);
          }
          catch (StorageException e) {
            // A failed scan must not let close mark an incomplete journal as clean.
            keyHashLogValid = false;
            throw e;
          }
          return durableMap.processKeys(key -> {
            //TODO RC: would be even better check keyHash even before key deserialization, to skip deserializing unmatched keys
            if (!hashes.test(keyDescriptor.getHashCode(key))) {
              return true;
            }
            return processor.process(key);
          });
        }
        else {
          //No keyHashLog, or it is invalid (not trustworthy)
          // => fallback to very ineffective but functionally correct way to apply IdFilter:
          return durableMap.processEntries((key, container) -> {
            boolean[] idFilterAccepted = new boolean[]{false};
            container.forEach((fileId, _) -> {
              if (idFilter.containsFileId(fileId)) {
                idFilterAccepted[0] = true;
                return false;//stop
              }
              return true;//continue
            });

            if (idFilterAccepted[0]) {
              return processor.process(key);
            }
            return true;
          });
        }
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
      catch (RuntimeException e) {
        throw unwrapCauseAndRethrow(e);
      }
    });
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

  private void recordKeyHash(Key key, int signedInputId) throws IOException {
    if (!keyHashLogValid || keyHashLog == null) return;
    try {
      keyHashLog.addKeyHashToVirtualFileMapping(key, signedInputId);
      keyHashLogDirty = true;
    }
    catch (StorageException e) {
      keyHashLogValid = false;
      throw new IOException(e);
    }
  }

  /// Reconstructs the journal before any query can use it to reject keys
  private void rebuildKeyHashLog(ValueContainerDurableMap<Key, Value> map, KeyHashLog<Key> log) throws IOException {
    try {
      boolean completed = map.processEntries((key, container) -> {
        container.forEach((inputId, _) -> {
          try {
            log.addKeyHashToVirtualFileMapping(key, inputId);
          }
          catch (StorageException e) {
            throw new UncheckedIOException(new IOException(e));
          }
          return true;
        });
        return true;
      });
      if (!completed) throw new IOException("The key hash log rebuild did not complete");
      log.flush();
    }
    catch (UncheckedIOException e) {
      throw e.getCause();
    }
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