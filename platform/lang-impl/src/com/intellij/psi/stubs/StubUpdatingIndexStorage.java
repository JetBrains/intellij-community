// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.MapReduceIndexMappingException;
import com.intellij.util.indexing.impl.storage.TransientFileContentIndex;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Internal
public final class StubUpdatingIndexStorage extends TransientFileContentIndex<Integer, SerializedStubTree, StubUpdatingIndexStorage.Data> {
  private static final Logger LOG = Logger.getInstance(StubUpdatingIndexStorage.class);

  private StubIndexImpl myStubIndex;
  private final @Nullable CompositeBinaryBuilderMap myCompositeBinaryBuilderMap = FileBasedIndex.USE_IN_MEMORY_INDEX
                                                                        ? null
                                                                        : new CompositeBinaryBuilderMap();
  private final @NotNull SerializationManagerEx mySerializationManager;

  StubUpdatingIndexStorage(@NotNull FileBasedIndexExtension<Integer, SerializedStubTree> extension,
                           @NotNull VfsAwareIndexStorageLayout<Integer, SerializedStubTree> layout,
                           @NotNull SerializationManagerEx serializationManager) throws IOException {
    super(extension, layout);
    mySerializationManager = serializationManager;
  }

  @Override
  protected void doFlush() throws IOException, StorageException {
    final StubIndexImpl stubIndex = getStubIndex();
    try {
      stubIndex.flush();
      mySerializationManager.flushNameStorage();
    }
    finally {
      super.doFlush();
    }
  }

  private @NotNull StubIndexImpl getStubIndex() {
    StubIndexImpl index = myStubIndex;
    if (index == null) {
      myStubIndex = index = (StubIndexImpl)StubIndex.getInstance();
    }
    return index;
  }

  @Override
  public @NotNull StorageUpdate mapInputAndPrepareUpdate(int inputId, @Nullable FileContent content)
    throws MapReduceIndexMappingException, ProcessCanceledException {
    try {
      StorageUpdate indexUpdate = withStubIndexingDiagnosticUpdate(inputId, content, super.mapInputAndPrepareUpdate(inputId, content));

      return () -> {
        try {
          return indexUpdate.update();
        }
        catch (Throwable t) {
          // ProcessCanceledException is not expected here
          LOG.error("Could not compute indexUpdateComputable", t);
          throw t;
        }
      };
    }
    catch (ProcessCanceledException pce) {
      if (FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES) {
        LOG.infoWithDebug("mapInputAndPrepareUpdate interrupted,inputId=" + inputId + "," + pce, new RuntimeException(pce));
      }
      throw pce;
    }
    catch (Throwable t) {
      LOG.warn("mapInputAndPrepareUpdate interrupted,inputId=" + inputId + "," + t, FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES ? t : null);
      throw t;
    }
  }

  public static @NotNull StorageUpdate withStubIndexingDiagnosticUpdate(int inputId, @Nullable FileContent content, @NotNull StorageUpdate indexUpdate) {
    IndexingStampInfo indexingStampInfo = content == null ? null : StubUpdatingIndex.calculateIndexingStamp(content);
    return () -> {
      boolean updateSuccessful = indexUpdate.update();
      if (updateSuccessful && !StaleIndexesChecker.isStaleIdDeletion()) {
        ((StubTreeLoaderImpl)StubTreeLoader.getInstance()).saveIndexingStampInfo(indexingStampInfo, inputId);
        if (FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES) {
          LOG.info("Updating IndexingStampInfo. inputId=" + inputId + ",result=" + updateSuccessful);
        }
      }
      else {
        // this is valuable information. Log it even without TRACE_STUB_INDEX_UPDATES flag
        LOG.info("Not updating IndexingStampInfo. inputId=" + inputId + ",result=" + updateSuccessful);
      }
      return updateSuccessful;
    };
  }

  @Override
  public void removeTransientDataForKeys(int inputId, @NotNull InputDataDiffBuilder<Integer, SerializedStubTree> diffBuilder) {
    Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> maps = getStubIndexMaps((StubCumulativeInputDiffBuilder)diffBuilder);

    if (FileBasedIndexEx.TRACE_STUB_INDEX_UPDATES) {
      LOG.info("removing transient data for inputId = " + inputId +
               ", keys = " + ((StubCumulativeInputDiffBuilder)diffBuilder).getKeys() +
               ", data = " + maps);
    }

    super.removeTransientDataForKeys(inputId, diffBuilder);
    removeTransientStubIndexKeys(inputId, maps);
  }

  private static void removeTransientStubIndexKeys(int inputId, @NotNull Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> indexedStubs) {
    StubIndexImpl stubIndex = (StubIndexImpl)StubIndex.getInstance();
    for (StubIndexKey key : indexedStubs.keySet()) {
      stubIndex.removeTransientDataForFile(key, inputId, indexedStubs.get(key));
    }
  }

  private static @NotNull Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> getStubIndexMaps(@NotNull StubCumulativeInputDiffBuilder diffBuilder) {
    SerializedStubTree tree = diffBuilder.getSerializedStubTree();
    return tree == null ? Collections.emptyMap() : tree.getStubIndicesValueMap();
  }

  @Override
  protected void doClear() throws StorageException, IOException {
    final StubIndexImpl stubIndex = (StubIndexImpl)StubIndex.getInstance();
    if (stubIndex != null) {
      stubIndex.clearAllIndices();
    }
    super.doClear();
  }

  @Override
  protected void doDispose() throws StorageException {
    try {
      super.doDispose();
    }
    finally {
      try {
        getStubIndex().dispose();
      }
      finally {
        mySerializationManager.performShutdown();
      }
    }
  }

  static final class Data extends IndexerIdHolder {
    private final FileType myFileType;

    Data(int indexerId, FileType type) {
      super(indexerId);
      myFileType = type;
    }
  }

  @Override
  public Data getFileIndexMetaData(@NotNull IndexedFile file) {
    IndexerIdHolder data = super.getFileIndexMetaData(file);
    FileType fileType = ProgressManager.getInstance().computeInNonCancelableSection(() -> file.getFileType());
    return new Data(data == null ? -1 : data.indexerId, fileType);
  }

  @Override
  public void setIndexedStateForFileOnFileIndexMetaData(int fileId,
                                                        @Nullable StubUpdatingIndexStorage.Data fileData,
                                                        boolean isProvidedByInfrastructureExtension) {
    super.setIndexedStateForFileOnFileIndexMetaData(fileId, fileData, isProvidedByInfrastructureExtension);
    LOG.assertTrue(fileData != null, "getFileIndexMetaData doesn't return null");
    setBinaryBuilderConfiguration(fileId, fileData);
  }

  @Override
  public void setIndexedStateForFile(int fileId, @NotNull IndexedFile file, boolean isProvidedByInfrastructureExtension) {
    super.setIndexedStateForFile(fileId, file, isProvidedByInfrastructureExtension);
    setBinaryBuilderConfiguration(fileId, file);
  }

  @Override
  public void setUnindexedStateForFile(int fileId) {
    super.setUnindexedStateForFile(fileId);
    resetBinaryBuilderConfiguration(fileId);
  }

  @Override
  protected FileIndexingState isIndexConfigurationUpToDate(int fileId, @NotNull IndexedFile file) {
    if (myCompositeBinaryBuilderMap == null) return FileIndexingState.UP_TO_DATE;
    try {
      return myCompositeBinaryBuilderMap.isUpToDateState(fileId, file.getFile());
    }
    catch (IOException e) {
      LOG.error(e);
      return FileIndexingState.OUT_DATED;
    }
  }

  @Override
  protected void setIndexConfigurationUpToDate(int fileId, @NotNull IndexedFile file) {
    setBinaryBuilderConfiguration(fileId, file);
  }

  private void setBinaryBuilderConfiguration(int fileId, @NotNull IndexedFile file) {
    if (myCompositeBinaryBuilderMap != null) {
      try {
        myCompositeBinaryBuilderMap.persistState(fileId, file.getFile());
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  private void setBinaryBuilderConfiguration(int fileId, @NotNull Data fileData) {
    if (myCompositeBinaryBuilderMap != null) {
      try {
        myCompositeBinaryBuilderMap.persistState(fileId, fileData.myFileType);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  private void resetBinaryBuilderConfiguration(int fileId) {
    if (myCompositeBinaryBuilderMap != null) {
      try {
        myCompositeBinaryBuilderMap.resetPersistedState(fileId);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  @Internal
  DataIndexer<Integer, SerializedStubTree, FileContent> getIndexer() {
    return indexer();
  }
}
