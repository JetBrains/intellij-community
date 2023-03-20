// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.indexing.impl.storage;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.ThrowableComputable;
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
import com.intellij.util.indexing.storage.MapReduceIndexBase;
import com.intellij.util.indexing.storage.SnapshotInputMappingIndex;
import com.intellij.util.indexing.storage.UpdatableSnapshotInputMappingIndex;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 */
public class VfsAwareMapReduceIndex<Key, Value, FileCachedData extends VfsAwareMapReduceIndex.IndexerIdHolder>
  extends MapReduceIndexBase<Key, Value, FileCachedData> {
  private static final Logger LOG = Logger.getInstance(VfsAwareMapReduceIndex.class);

  @ApiStatus.Internal
  public static final int VERSION = 0;

  static {
    final Application app = ApplicationManager.getApplication();

    if (!IndexDebugProperties.DEBUG) {
      IndexDebugProperties.DEBUG = (app.isEAP() || app.isInternal()) &&
                                   !ApplicationManagerEx.isInStressTest() &&
                                   !ApplicationManagerEx.isInIntegrationTest();
    }

    if (!IndexDebugProperties.IS_UNIT_TEST_MODE) {
      IndexDebugProperties.IS_UNIT_TEST_MODE = app.isUnitTestMode();
    }
  }

  @SuppressWarnings("rawtypes")
  private final PersistentSubIndexerRetriever mySubIndexerRetriever;
  private final SnapshotInputMappingIndex<Key, Value, FileContent> mySnapshotInputMappings;
  private final boolean myUpdateMappings;

  public VfsAwareMapReduceIndex(@NotNull FileBasedIndexExtension<Key, Value> extension,
                                @NotNull VfsAwareIndexStorageLayout<Key, Value> indexStorageLayout) throws IOException {
    this(extension,
         () -> indexStorageLayout.openIndexStorage(),
         () -> indexStorageLayout.openForwardIndex(),
         indexStorageLayout.getForwardIndexAccessor(),
         () -> indexStorageLayout.createOrClearSnapshotInputMappings());
  }

  protected VfsAwareMapReduceIndex(@NotNull FileBasedIndexExtension<Key, Value> extension,
                                   @NotNull ThrowableComputable<? extends IndexStorage<Key, Value>, ? extends IOException> storage,
                                   @Nullable ThrowableComputable<? extends ForwardIndex, ? extends IOException> forwardIndexMap,
                                   @Nullable ForwardIndexAccessor<Key, Value> forwardIndexAccessor,
                                   @Nullable ThrowableComputable<? extends SnapshotInputMappingIndex<Key, Value, FileContent>, ? extends IOException> snapshotInputMappings) throws IOException {
    super(extension, storage, forwardIndexMap, forwardIndexAccessor);
    SnapshotInputMappingIndex<Key, Value, FileContent> inputMappings;
    try {
      inputMappings = snapshotInputMappings == null ? null : snapshotInputMappings.compute();
    } catch (IOException e) {
      tryDispose();
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
    if (FileBasedIndex.isCompositeIndexer(myIndexer)) {
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
        tryDispose();
        throw e;
      }
    } else {
      mySubIndexerRetriever = null;
    }
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

  @Override
  protected Logger getLogger() {
    return LOG;
  }

  @Override
  protected void checkNonCancellableSection() {
    LOG.assertTrue(ProgressManager.getInstance().isInNonCancelableSection());
  }

  @NotNull
  @Override
  protected final InputData<Key, Value> mapInput(int inputId, @Nullable FileContent content) {
    InputData<Key, Value> data;
    boolean containsSnapshotData = true;
    boolean isPhysical = content instanceof FileContentImpl && !((FileContentImpl)content).isTransientContent();
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

  public static class IndexerIdHolder {
    public int indexerId;

    public IndexerIdHolder(int indexerId) {
      this.indexerId = indexerId;
    }
  }

  @Override
  public FileCachedData getFileIndexMetaData(@NotNull IndexedFile file) {
    if (mySubIndexerRetriever != null) {
      try {
        IndexerIdHolder holder = ProgressManager.getInstance()
          .computeInNonCancelableSection(() -> new IndexerIdHolder(mySubIndexerRetriever.getFileIndexerId(file)));
        LOG.assertTrue(holder != null,
                       "getFileIndexMetaData() shouldn't have returned null in " + getClass() + ", " + myIndexId.getName());
        return (FileCachedData)holder;
      }
      catch (IOException e) {
        LOG.error(e);
        // Index would be rebuilt, and exception would be logged with INFO severity
        // in com.intellij.util.indexing.FileBasedIndexImpl.requestIndexRebuildOnException
        throw new RuntimeException(e);
      }
    }
    return null;
  }

  /**
   * @return value < 0 means that no sub indexer id corresponds to the specified file
   */
  protected int getStoredFileSubIndexerId(int fileId) {
    if (mySubIndexerRetriever == null) throw new IllegalStateException("not a composite indexer");
    try {
      return mySubIndexerRetriever.getStoredFileIndexerId(fileId);
    }
    catch (IOException e) {
      LOG.error(e);
      return -4;
    }
  }

  public <SubIndexerVersion> @Nullable SubIndexerVersion getStoredSubIndexerVersion(int fileId) {
    int indexerId = getStoredFileSubIndexerId(fileId);
    if (indexerId < 0) return null;
    try {
      return (SubIndexerVersion)mySubIndexerRetriever.getVersionByIndexerId(indexerId);
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  @Override
  public void setIndexedStateForFileOnFileIndexMetaData(int fileId, @Nullable FileCachedData fileData) {
    IndexingStamp.setFileIndexedStateCurrent(fileId, (ID<?, ?>)myIndexId);
    if (mySubIndexerRetriever != null) {
      LOG.assertTrue(fileData != null,
                     "getFileIndexMetaData() shouldn't have returned null in " + getClass() + ", " + myIndexId.getName());
      try {
        mySubIndexerRetriever.setFileIndexerId(fileId, fileData.indexerId);
      }
      catch (IOException e) {
        LOG.error(e);
      }
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
  protected void requestRebuild(@NotNull Throwable ex) {
    Runnable action = () -> FileBasedIndex.getInstance().requestRebuild((ID<?, ?>)myIndexId, ex);
    Application app = ApplicationManager.getApplication();
    if (app.isUnitTestMode() || app.isHeadlessEnvironment()) {
      // avoid deadlock due to synchronous update in DumbServiceImpl#queueTask
      app.invokeLater(action);
    }
    else if (app.isReadAccessAllowed()) {
      IndexDataInitializer.submitGenesisTask(() -> {
        action.run();
        return null;
      });
    }
    else {
      action.run();
    }
  }

  @Override
  protected @NotNull ValueSerializationProblemReporter getSerializationProblemReporter() {
    return problem -> {
      PluginId pluginId = ((ID<?, ?>)myIndexId).getPluginId();
      if (pluginId != null) {
        LOG.error(new PluginException(problem, pluginId));
      }
      else {
        LOG.error(problem);
      }
    };
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
