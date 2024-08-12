// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import com.intellij.util.indexing.impl.IndexDebugProperties;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.ValueSerializationProblemReporter;
import com.intellij.util.indexing.impl.forward.AbstractMapForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import com.intellij.util.indexing.impl.perFileVersion.PersistentSubIndexerRetriever;
import com.intellij.util.indexing.storage.MapReduceIndexBase;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

@Internal
public class VfsAwareMapReduceIndex<Key, Value, FileCachedData extends VfsAwareMapReduceIndex.IndexerIdHolder>
  extends MapReduceIndexBase<Key, Value, FileCachedData> {
  private static final Logger LOG = Logger.getInstance(VfsAwareMapReduceIndex.class);

  @Internal
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

  public VfsAwareMapReduceIndex(@NotNull FileBasedIndexExtension<Key, Value> extension,
                                @NotNull VfsAwareIndexStorageLayout<Key, Value> indexStorageLayout) throws IOException {
    this(extension,
         () -> indexStorageLayout.openIndexStorage(),
         () -> indexStorageLayout.openForwardIndex(),
         indexStorageLayout.getForwardIndexAccessor());
  }

  protected VfsAwareMapReduceIndex(@NotNull FileBasedIndexExtension<Key, Value> extension,
                                   @NotNull ThrowableComputable<? extends IndexStorage<Key, Value>, ? extends IOException> storage,
                                   @Nullable ThrowableComputable<? extends ForwardIndex, ? extends IOException> forwardIndexMap,
                                   @Nullable ForwardIndexAccessor<Key, Value> forwardIndexAccessor) throws IOException {
    super(extension, storage, forwardIndexMap, forwardIndexAccessor);

    if (FileBasedIndex.isCompositeIndexer(myIndexer)) {
      try {
        // noinspection unchecked,rawtypes
        mySubIndexerRetriever = new PersistentSubIndexerRetriever((ID)myIndexId,
                                                                  extension.getVersion(),
                                                                  (CompositeDataIndexer)myIndexer);
      }
      catch (IOException e) {
        tryDispose();
        throw e;
      }
    } else {
      mySubIndexerRetriever = null;
    }
  }

  @Override
  protected Logger getLogger() {
    return LOG;
  }

  @Override
  protected void checkNonCancellableSection() {
    LOG.assertTrue(ProgressManager.getInstance().isInNonCancelableSection());
  }

  public @NotNull InputDataDiffBuilder<Key, Value> getKeysDiffBuilder(int inputId, @NotNull Map<Key, Value> keysAndValues) throws IOException {
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
  public void setIndexedStateForFileOnFileIndexMetaData(int fileId,
                                                        @Nullable FileCachedData fileData,
                                                        boolean isProvidedByInfrastructureExtension) {
    IndexingStamp.setFileIndexedStateCurrent(fileId, (ID<?, ?>)myIndexId, isProvidedByInfrastructureExtension);
    if (mySubIndexerRetriever != null) {
      if (fileData == null) {
        LOG.error("getFileIndexMetaData() shouldn't have returned null in " + getClass() + ", " + myIndexId.getName());
      }
      else {
        try {
          mySubIndexerRetriever.setFileIndexerId(fileId, fileData.indexerId);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }

  @Override
  public void setIndexedStateForFile(int fileId, @NotNull IndexedFile file, boolean isProvidedByInfrastructureExtension) {
    IndexingStamp.setFileIndexedStateCurrent(fileId, (ID<?, ?>)myIndexId, isProvidedByInfrastructureExtension);
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

  @Override
  public boolean isDirty() {
    if (super.isDirty()) {
      return true;
    }

    if (mySubIndexerRetriever != null) {
      return mySubIndexerRetriever.isDirty();
    }

    return false;
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
    if (mySubIndexerRetriever != null) {
      mySubIndexerRetriever.flush();
    }
  }

  @Override
  protected void doDispose() throws StorageException {
    try {
      super.doDispose();
    } finally {
      IOUtil.closeSafe(LOG, mySubIndexerRetriever);
    }
  }
}
