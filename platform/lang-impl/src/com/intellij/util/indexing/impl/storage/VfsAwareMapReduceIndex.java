// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.indexing.impl.storage;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.indexing.impl.forward.*;
import com.intellij.util.indexing.impl.perFileVersion.PersistentSubIndexerRetriever;
import com.intellij.util.indexing.memory.InMemoryForwardIndex;
import com.intellij.util.indexing.snapshot.SnapshotInputMappingIndex;
import com.intellij.util.indexing.snapshot.SnapshotInputMappings;
import com.intellij.util.indexing.snapshot.SnapshotSingleValueIndexStorage;
import com.intellij.util.indexing.snapshot.UpdatableSnapshotInputMappingIndex;
import com.intellij.util.io.IOUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * @author Eugene Zhuravlev
 */
public class VfsAwareMapReduceIndex<Key, Value> extends MapReduceIndex<Key, Value, FileContent> implements UpdatableIndex<Key, Value, FileContent>{
  private static final Logger LOG = Logger.getInstance(VfsAwareMapReduceIndex.class);

  @ApiStatus.Internal
  public static final int VERSION = 0;

  static {
    final Application app = ApplicationManager.getApplication();

    if (!IndexDebugProperties.DEBUG) {
      IndexDebugProperties.DEBUG = (app.isEAP() || app.isInternal()) && !ApplicationInfoImpl.isInStressTest();
    }

    if (!IndexDebugProperties.IS_UNIT_TEST_MODE) {
      IndexDebugProperties.IS_UNIT_TEST_MODE = app.isUnitTestMode();
    }
  }

  private final AtomicBoolean myInMemoryMode = new AtomicBoolean();
  private final ConcurrentIntObjectMap<Map<Key, Value>> myInMemoryKeysAndValues = ContainerUtil.createConcurrentIntObjectMap();

  @SuppressWarnings("rawtypes")
  private final PersistentSubIndexerRetriever mySubIndexerRetriever;
  private final SnapshotInputMappingIndex<Key, Value, FileContent> mySnapshotInputMappings;
  private final boolean myUpdateMappings;
  private final boolean mySingleEntryIndex;

  public VfsAwareMapReduceIndex(@NotNull IndexExtension<Key, Value, FileContent> extension,
                                @NotNull IndexStorage<Key, Value> storage) throws IOException {
    this(extension,
         storage,
         hasSnapshotMapping(extension) ? new SnapshotInputMappings<>(extension, getForwardIndexAccessor(extension)) : null);
    if (!(myIndexId instanceof ID<?, ?>)) {
      throw new IllegalArgumentException("myIndexId should be instance of com.intellij.util.indexing.ID");
    }
  }

  public VfsAwareMapReduceIndex(@NotNull IndexExtension<Key, Value, FileContent> extension,
                                @NotNull IndexStorage<Key, Value> storage,
                                @Nullable SnapshotInputMappings<Key, Value> snapshotInputMappings) throws IOException {
    this(extension,
         storage,
         snapshotInputMappings != null ? new IntMapForwardIndex(snapshotInputMappings.getInputIndexStorageFile(), true)
                                       : getForwardIndexMap(extension),
         snapshotInputMappings != null ? snapshotInputMappings.getForwardIndexAccessor() : getForwardIndexAccessor(extension),
         snapshotInputMappings, null);
  }

  public VfsAwareMapReduceIndex(@NotNull IndexExtension<Key, Value, FileContent> extension,
                                @NotNull IndexStorage<Key, Value> storage,
                                @Nullable ForwardIndex forwardIndexMap,
                                @Nullable ForwardIndexAccessor<Key, Value> forwardIndexAccessor,
                                @Nullable SnapshotInputMappings<Key, Value> snapshotInputMappings,
                                @Nullable ReadWriteLock lock) {
    super(extension, storage, forwardIndexMap, forwardIndexAccessor, lock);
    if (storage instanceof TransientChangesIndexStorage && snapshotInputMappings != null) {
      VfsAwareIndexStorage<Key, Value> backendStorage = ((TransientChangesIndexStorage<Key, Value>)storage).getBackendStorage();
      if (backendStorage instanceof SnapshotSingleValueIndexStorage) {
        LOG.assertTrue(forwardIndexMap instanceof IntForwardIndex);
        ((SnapshotSingleValueIndexStorage<Key, Value>)backendStorage).init(snapshotInputMappings, ((IntForwardIndex)forwardIndexMap));
      }
    }
    if (isCompositeIndexer(myIndexer)) {
      try {
        // noinspection unchecked,rawtypes
        mySubIndexerRetriever = new PersistentSubIndexerRetriever((ID)myIndexId,
                                                                  extension.getVersion(),
                                                                  (CompositeDataIndexer) myIndexer);
        if (snapshotInputMappings != null) {
          snapshotInputMappings.setSubIndexerRetriever(mySubIndexerRetriever);
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    } else {
      mySubIndexerRetriever = null;
    }
    mySnapshotInputMappings = snapshotInputMappings;
    myUpdateMappings = mySnapshotInputMappings != null;
    mySingleEntryIndex = extension instanceof SingleEntryFileBasedIndexExtension;
    installMemoryModeListener();
  }

  @Override
  public void dumpStatistics() {
    if (mySnapshotInputMappings instanceof SnapshotInputMappings<?, ?>) {
      ((SnapshotInputMappings<?, ?>) mySnapshotInputMappings).dumpStatistics();
    }
  }

  public static boolean isCompositeIndexer(@NotNull DataIndexer<?, ?, ?> indexer) {
    return indexer instanceof CompositeDataIndexer && !FileBasedIndex.USE_IN_MEMORY_INDEX;
  }

  public static <Key, Value> boolean hasSnapshotMapping(@NotNull IndexExtension<Key, Value, ?> indexExtension) {
    //noinspection unchecked
    return indexExtension instanceof FileBasedIndexExtension &&
           ((FileBasedIndexExtension<Key, Value>)indexExtension).hasSnapshotMapping() &&
           FileBasedIndex.ourSnapshotMappingsEnabled &&
           !FileBasedIndex.USE_IN_MEMORY_INDEX;
  }

  @NotNull
  @Override
  protected final InputData<Key, Value> mapInput(int inputId, @Nullable FileContent content) {
    InputData<Key, Value> data;
    boolean containsSnapshotData = true;
    boolean isPhysical = content instanceof FileContentImpl && ((FileContentImpl)content).isPhysicalContent();
    if (mySnapshotInputMappings != null && isPhysical) {
      try {
        data = mySnapshotInputMappings.readData(content);
        if (data != null) {
          return data;
        } else {
          containsSnapshotData = !myUpdateMappings;
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    data = super.mapInput(inputId, content);
    if (!containsSnapshotData) {
      try {
        return ((UpdatableSnapshotInputMappingIndex<Key, Value, FileContent>)mySnapshotInputMappings).putData(content, data);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return data;
  }

  @NotNull
  @Override
  protected InputDataDiffBuilder<Key, Value> getKeysDiffBuilder(int inputId) throws IOException {
    if (mySnapshotInputMappings != null && !myInMemoryMode.get()) {
      return super.getKeysDiffBuilder(inputId);
    }
    if (myInMemoryMode.get()) {
      Map<Key, Value> keysAndValues = myInMemoryKeysAndValues.get(inputId);
      if (keysAndValues != null) {
        return getKeysDiffBuilder(inputId, keysAndValues);
      }
    }
    return super.getKeysDiffBuilder(inputId);
  }

  @NotNull
  public InputDataDiffBuilder<Key, Value> getKeysDiffBuilder(int inputId, @NotNull Map<Key, Value> keysAndValues) throws IOException {
    return ((AbstractMapForwardIndexAccessor<Key, Value, ?>)getForwardIndexAccessor()).createDiffBuilderByMap(inputId, keysAndValues);
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
  public void setIndexedStateForFile(int fileId, @NotNull IndexedFile file) {
    IndexingStamp.setFileIndexedStateCurrent(fileId, (ID<?, ?>)myIndexId);
    if (mySubIndexerRetriever != null) {
      try {
        mySubIndexerRetriever.setIndexedState(fileId, file);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public void invalidateIndexedStateForFile(int fileId) {
    IndexingStamp.setFileIndexedStateOutdated(fileId, (ID<?, ?>)myIndexId);
  }

  @Override
  public void setUnindexedStateForFile(int fileId) {
    IndexingStamp.setFileIndexedStateUnindexed(fileId, (ID<?, ?>)myIndexId);
    if (mySubIndexerRetriever != null) {
      try {
        mySubIndexerRetriever.setUnindexedState(fileId);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  public @NotNull FileIndexingState getIndexingStateForFile(int fileId, @NotNull IndexedFile file) {
    FileIndexingState baseState = IndexingStamp.isFileIndexedStateCurrent(fileId, (ID<?, ?>)myIndexId);
    if (baseState != FileIndexingState.UP_TO_DATE) {
      return baseState;
    }
    if (mySubIndexerRetriever == null) return FileIndexingState.UP_TO_DATE;
    if (!(file instanceof FileContent)) {
      if (((CompositeDataIndexer<?, ?, ?, ?>)myIndexer).requiresContentForSubIndexerEvaluation(file)) {
        FileIndexingState indexConfigurationState = isIndexConfigurationUpToDate(fileId, file);
        // baseState == UP_TO_DATE => no need to reindex this file
        return indexConfigurationState == FileIndexingState.OUT_DATED ? FileIndexingState.OUT_DATED : FileIndexingState.UP_TO_DATE;
      }
    }
    try {
      FileIndexingState subIndexerState = mySubIndexerRetriever.getSubIndexerState(fileId, file);
      if (subIndexerState == FileIndexingState.UP_TO_DATE) {
        if (file instanceof FileContent && ((CompositeDataIndexer<?, ?, ?, ?>)myIndexer).requiresContentForSubIndexerEvaluation(file)) {
          setIndexConfigurationUpToDate(fileId, file);
        }
        return FileIndexingState.UP_TO_DATE;
      }
      if (subIndexerState == FileIndexingState.NOT_INDEXED) {
        // baseState == UP_TO_DATE => no need to reindex this file
        return FileIndexingState.UP_TO_DATE;
      }
      return subIndexerState;
    }
    catch (IOException e) {
      LOG.error(e);
      return FileIndexingState.OUT_DATED;
    }
  }

  protected FileIndexingState isIndexConfigurationUpToDate(int fileId, @NotNull IndexedFile file) {
    return FileIndexingState.OUT_DATED;
  }

  protected void setIndexConfigurationUpToDate(int fileId, @NotNull IndexedFile file) { }

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
      myModificationStamp.incrementAndGet();
    }
  }


  @Override
  public void setBufferingEnabled(boolean enabled) {
    ((TransientChangesIndexStorage<Key, Value>)getStorage()).setBufferingEnabled(enabled);
  }

  @Override
  public void cleanupMemoryStorage() {
    TransientChangesIndexStorage<Key, Value> memStorage = (TransientChangesIndexStorage<Key, Value>)getStorage();
    ConcurrencyUtil.withLock(getLock().writeLock(), () -> {
      if (memStorage.clearMemoryMap()) {
        myModificationStamp.incrementAndGet();
      }
    });
    memStorage.fireMemoryStorageCleared();
  }

  @TestOnly
  @Override
  public void cleanupForNextTest() {
    IndexStorage<Key, Value> memStorage = getStorage();
    ConcurrencyUtil.withLock(getLock().readLock(), () -> memStorage.clearCaches());
  }

  @Override
  public boolean processAllKeys(@NotNull Processor<? super Key> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter idFilter) throws StorageException {
    return ConcurrencyUtil.withLock(getLock().readLock(), () ->
      ((VfsAwareIndexStorage<Key, Value>)myStorage).processKeys(processor, scope, idFilter)
    );
  }

  @NotNull
  @Override
  public Map<Key, Value> getIndexedFileData(int fileId) throws StorageException {
    return ConcurrencyUtil.withLock(getLock().readLock(), () -> {
      try {
        return Collections.unmodifiableMap(ContainerUtil.notNullize(getNullableIndexedData(fileId)));
      }
      catch (IOException e) {
        throw new StorageException(e);
      }
    });
  }

  @Nullable
  private Map<Key, Value> getNullableIndexedData(int fileId) throws IOException, StorageException {
    if (myInMemoryMode.get()) {
      Map<Key, Value> map = myInMemoryKeysAndValues.get(fileId);
      if (map != null) return map;
    }
    // in future we will get rid of forward index for SingleEntryFileBasedIndexExtension
    if (mySingleEntryIndex) {
      @SuppressWarnings("unchecked")
      Key key = (Key)(Object)fileId;
      Ref<Map<Key, Value>> result = new Ref<>(Collections.emptyMap());
      ValueContainer<Value> container = getData(key);
      container.forEach((id, value) -> {
        boolean acceptNullValues = ((SingleEntryIndexer<?>)myIndexer).isAcceptNullValues();
        if (value != null || acceptNullValues) {
          result.set(Collections.singletonMap(key, value));
        }
        return false;
      });
      return result.get();
    }
    if (getForwardIndexAccessor() instanceof AbstractMapForwardIndexAccessor) {
      ByteArraySequence serializedInputData = getForwardIndex().get(fileId);
      AbstractMapForwardIndexAccessor<Key, Value, ?> forwardIndexAccessor = (AbstractMapForwardIndexAccessor<Key, Value, ?>)getForwardIndexAccessor();
      return forwardIndexAccessor.convertToInputDataMap(fileId, serializedInputData);
    }
    LOG.error("Can't fetch indexed data for index " + myIndexId.getName());
    return null;
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
      ApplicationManager.getApplication().invokeLater(action);
    } else {
      action.run();
    }
  }

  @Override
  public void updateWithMap(@NotNull AbstractUpdateData<Key, Value> updateData) throws StorageException {
    try {
      super.updateWithMap(updateData);
    }
    catch (ProcessCanceledException e) {
      LOG.error("ProcessCancelledException is not expected here!", e);
      throw e;
    }
  }

  @Override
  protected void doClear() throws StorageException, IOException {
    super.doClear();
    if (mySnapshotInputMappings != null && myUpdateMappings) {
      try {
        ((UpdatableSnapshotInputMappingIndex<Key, Value, FileContent>)mySnapshotInputMappings).clear();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    if (mySubIndexerRetriever != null) {
      try {
        mySubIndexerRetriever.clear();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @Override
  protected void doFlush() throws IOException, StorageException {
    super.doFlush();
    if (mySnapshotInputMappings != null && myUpdateMappings) {
      ((UpdatableSnapshotInputMappingIndex<Key, Value, FileContent>)mySnapshotInputMappings).flush();
    }
    if (mySubIndexerRetriever != null) {
      mySubIndexerRetriever.flush();
    }
  }

  @Override
  protected void doDispose() throws StorageException {
    try {
      super.doDispose();
    } finally {
      IOUtil.closeSafe(LOG, mySnapshotInputMappings, mySubIndexerRetriever);
    }
  }

  @ApiStatus.Internal
  @NotNull
  public static <Key, Value> AbstractMapForwardIndexAccessor<Key, Value, ?> getForwardIndexAccessor(@NotNull IndexExtension<Key, Value, ?> indexExtension) {
    if (!(indexExtension instanceof SingleEntryFileBasedIndexExtension) || FileBasedIndex.USE_IN_MEMORY_INDEX) {
      return new MapForwardIndexAccessor<>(new InputMapExternalizer<>(indexExtension));
    }
    //noinspection unchecked,rawtypes
    return new SingleEntryIndexForwardIndexAccessor(indexExtension);
  }

  @Nullable
  private static ForwardIndex getForwardIndexMap(@NotNull IndexExtension<?, ?, ?> indexExtension)
    throws IOException {
    if (!shouldCreateForwardIndex(indexExtension)) return null;
    if (FileBasedIndex.USE_IN_MEMORY_INDEX) return new InMemoryForwardIndex();
    if (indexExtension instanceof SingleEntryFileBasedIndexExtension<?>) return new EmptyForwardIndex(); // indexStorage and forwardIndex are same here
    File indexStorageFile = IndexInfrastructure.getInputIndexStorageFile((ID<?, ?>)indexExtension.getName());
    return new PersistentMapBasedForwardIndex(indexStorageFile.toPath(), false, false);
  }

  private static boolean shouldCreateForwardIndex(@NotNull IndexExtension<?, ?, ?> indexExtension) {
    return !hasSnapshotMapping(indexExtension);
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

  @NotNull
  private Collection<Key> getKeys(InputDataDiffBuilder<Key, ?> builder) throws StorageException {
    if (builder instanceof DirectInputDataDiffBuilder<?, ?>) {
      //noinspection unchecked
      return ((DirectInputDataDiffBuilder<Key, ?>)builder).getKeys();
    }

    LOG.error("Input data diff builder must be an instance of DirectInputDataDiffBuilder for index " + myIndexId.getName());

    Set<Key> keys = new THashSet<>();
    builder.differentiate(
      Collections.emptyMap(),
      (key, value, inputId) -> { },
      (key, value, inputId) -> {},
      (key, inputId) -> keys.add(key)
    );
    return keys;
  }
}
