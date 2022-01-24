// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Computable;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.impl.InputDataDiffBuilder;
import com.intellij.util.indexing.impl.MapReduceIndexMappingException;
import com.intellij.util.indexing.impl.storage.TransientFileContentIndex;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

final class StubUpdatingIndexStorage extends TransientFileContentIndex<Integer, SerializedStubTree> {
  private static final Logger LOG = Logger.getInstance(StubUpdatingIndexStorage.class);

  private StubIndexImpl myStubIndex;
  @Nullable
  private final CompositeBinaryBuilderMap myCompositeBinaryBuilderMap = FileBasedIndex.USE_IN_MEMORY_INDEX
                                                                        ? null
                                                                        : new CompositeBinaryBuilderMap();
  private final @NotNull SerializationManagerEx mySerializationManager;

  StubUpdatingIndexStorage(@NotNull FileBasedIndexExtension<Integer, SerializedStubTree> extension,
                           @NotNull VfsAwareIndexStorageLayout<Integer, SerializedStubTree> layout,
                           @NotNull SerializationManagerEx serializationManager) throws IOException {
    super(extension, layout, null);
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

  @NotNull
  private StubIndexImpl getStubIndex() {
    StubIndexImpl index = myStubIndex;
    if (index == null) {
      myStubIndex = index = (StubIndexImpl)StubIndex.getInstance();
    }
    return index;
  }

  @Override
  public @NotNull Computable<Boolean> mapInputAndPrepareUpdate(int inputId, @Nullable FileContent content)
    throws MapReduceIndexMappingException, ProcessCanceledException {
    Computable<Boolean> indexUpdateComputable = super.mapInputAndPrepareUpdate(inputId, content);
    IndexingStampInfo indexingStampInfo = content == null ? null : StubUpdatingIndex.calculateIndexingStamp(content);

    return () -> {
      try {
        Boolean result = indexUpdateComputable.compute();
        if (Boolean.TRUE.equals(result) && !StaleIndexesChecker.isStaleIdDeletion()) {
          StubTreeLoaderImpl.saveIndexingStampInfo(indexingStampInfo, inputId);
        }
        return result;
      }
      catch (ProcessCanceledException e) {
        LOG.error("ProcessCanceledException is not expected here", e);
        throw e;
      }
    };
  }

  @Override
  public void removeTransientDataForKeys(int inputId, @NotNull InputDataDiffBuilder<Integer, SerializedStubTree> diffBuilder) {
    Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> maps = getStubIndexMaps((StubCumulativeInputDiffBuilder)diffBuilder);

    if (FileBasedIndexEx.DO_TRACE_STUB_INDEX_UPDATE) {
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

  @NotNull
  private static Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> getStubIndexMaps(@NotNull StubCumulativeInputDiffBuilder diffBuilder) {
    SerializedStubTree tree = diffBuilder.getSerializedStubTree();
    return tree == null ? Collections.emptyMap() : tree.getStubIndicesValueMap();
  }

  @Override
  protected void doClear() throws StorageException, IOException {
    final StubIndexImpl stubIndex = (StubIndexImpl)StubIndexImpl.getInstance();
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

  @Override
  public void setIndexedStateForFile(int fileId, @NotNull IndexedFile file) {
    super.setIndexedStateForFile(fileId, file);
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
}
