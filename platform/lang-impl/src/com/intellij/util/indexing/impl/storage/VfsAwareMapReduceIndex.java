// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.indexing.impl.storage;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.indexing.impl.forward.AbstractMapForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.IntForwardIndex;
import com.intellij.util.indexing.impl.perFileVersion.PersistentSubIndexerRetriever;
import com.intellij.util.indexing.snapshot.SnapshotInputMappingException;
import com.intellij.util.indexing.snapshot.SnapshotInputMappings;
import com.intellij.util.indexing.snapshot.SnapshotInputMappingsStatistics;
import com.intellij.util.indexing.snapshot.SnapshotSingleValueIndexStorage;
import com.intellij.util.indexing.storage.SnapshotInputMappingIndex;
import com.intellij.util.indexing.storage.UpdatableSnapshotInputMappingIndex;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
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

  @SuppressWarnings("rawtypes")
  private final PersistentSubIndexerRetriever mySubIndexerRetriever;
  private final SnapshotInputMappingIndex<Key, Value, FileContent> mySnapshotInputMappings;
  private final boolean myUpdateMappings;
  private final boolean mySingleEntryIndex;

  public VfsAwareMapReduceIndex(@NotNull FileBasedIndexExtension<Key, Value> extension,
                                @NotNull VfsAwareIndexStorageLayout<Key, Value> indexStorageLayout,
                                @Nullable ReadWriteLock lock) throws IOException {
    this(extension,
         () -> indexStorageLayout.createOrClearIndexStorage(),
         () -> indexStorageLayout.createOrClearForwardIndex(),
         indexStorageLayout.getForwardIndexAccessor(),
         () -> indexStorageLayout.createOrClearSnapshotInputMappings(),
         lock);
  }

  protected VfsAwareMapReduceIndex(@NotNull FileBasedIndexExtension<Key, Value> extension,
                                   @NotNull ThrowableComputable<? extends IndexStorage<Key, Value>, ? extends IOException> storage,
                                   @Nullable ThrowableComputable<? extends ForwardIndex, ? extends IOException> forwardIndexMap,
                                   @Nullable ForwardIndexAccessor<Key, Value> forwardIndexAccessor,
                                   @Nullable ThrowableComputable<? extends SnapshotInputMappingIndex<Key, Value, FileContent>, ? extends IOException> snapshotInputMappings,
                                   @Nullable ReadWriteLock lock) throws IOException {
    super(extension, storage, forwardIndexMap, forwardIndexAccessor, lock);
    if (!(myIndexId instanceof ID<?, ?>)) {
      throw new IllegalArgumentException("myIndexId should be instance of com.intellij.util.indexing.ID");
    }
    SnapshotInputMappingIndex<Key, Value, FileContent> inputMappings;
    try {
      inputMappings = snapshotInputMappings == null ? null : snapshotInputMappings.compute();
    } catch (IOException e) {
      clearAndDispose();
      throw e;
    }
    mySnapshotInputMappings = inputMappings;
    myUpdateMappings = mySnapshotInputMappings != null;

    if (inputMappings != null) {
      @NotNull IndexStorage<Key, Value> backendStorage = getStorage();
      if (backendStorage instanceof TransientChangesIndexStorage) {
        backendStorage = ((TransientChangesIndexStorage<Key, Value>)backendStorage).getBackendStorage();
      }
      if (backendStorage instanceof SnapshotSingleValueIndexStorage) {
        LOG.assertTrue(forwardIndexMap instanceof IntForwardIndex);
        ((SnapshotSingleValueIndexStorage<Key, Value>)backendStorage).init((SnapshotInputMappings<Key, Value>)inputMappings,
                                                                           ((IntForwardIndex)forwardIndexMap));
      }
    }
    if (isCompositeIndexer(myIndexer)) {
      try {
        // noinspection unchecked,rawtypes
        mySubIndexerRetriever = new PersistentSubIndexerRetriever((ID)myIndexId,
                                                                  extension.getVersion(),
                                                                  (CompositeDataIndexer)myIndexer);
        if (inputMappings instanceof SnapshotInputMappings) {
          ((SnapshotInputMappings<?, ?>)inputMappings).setSubIndexerRetriever(mySubIndexerRetriever);
        }
      }
      catch (IOException e) {
        clearAndDispose();
        throw e;
      }
    } else {
      mySubIndexerRetriever = null;
    }
    mySingleEntryIndex = extension instanceof SingleEntryFileBasedIndexExtension;
  }

  public void resetSnapshotInputMappingsStatistics() {
    if (mySnapshotInputMappings instanceof SnapshotInputMappings<?, ?>) {
      ((SnapshotInputMappings<?, ?>)mySnapshotInputMappings).resetStatistics();
    }
  }

  public @Nullable SnapshotInputMappingsStatistics dumpSnapshotInputMappingsStatistics() {
    if (mySnapshotInputMappings instanceof SnapshotInputMappings<?, ?>) {
      return ((SnapshotInputMappings<?, ?>) mySnapshotInputMappings).dumpStatistics();
    }
    return null;
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
        throw new SnapshotInputMappingException(e);
      }
    }
    data = super.mapInput(inputId, content);
    if (!containsSnapshotData) {
      try {
        return ((UpdatableSnapshotInputMappingIndex<Key, Value, FileContent>)mySnapshotInputMappings).putData(content, data);
      }
      catch (IOException e) {
        throw new SnapshotInputMappingException(e);
      }
    }
    return data;
  }

  @NotNull
  public InputDataDiffBuilder<Key, Value> getKeysDiffBuilder(int inputId, @NotNull Map<Key, Value> keysAndValues) throws IOException {
    return ((AbstractMapForwardIndexAccessor<Key, Value, ?>)getForwardIndexAccessor()).createDiffBuilderByMap(inputId, keysAndValues);
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
  protected Map<Key, Value> getNullableIndexedData(int fileId) throws IOException, StorageException {
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
  public void setBufferingEnabled(boolean enabled) {
    // TODO to be removed
    throw new UnsupportedOperationException();
  }

  @Override
  public void cleanupMemoryStorage() {
    // TODO to be removed
    throw new UnsupportedOperationException();
  }

  @Override
  public void cleanupForNextTest() {
    // TODO to be removed
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeTransientDataForFile(int inputId) {
    // TODO to be removed
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeTransientDataForKeys(int inputId,
                                         @NotNull InputDataDiffBuilder<Key, Value> diffBuilder) {
    // TODO to be removed
    throw new UnsupportedOperationException();
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
}
