// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.StubFileElementType;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class PerFileElementTypeStubModificationTracker implements StubIndexImpl.FileUpdateProcessor {
  static final Logger LOG = Logger.getInstance(PerFileElementTypeStubModificationTracker.class);
  public static final int PRECISE_CHECK_THRESHOLD =
    SystemProperties.getIntProperty("stub.index.per.file.element.type.modification.tracker.precise.check.threshold", 20);

  private final ConcurrentMap<String, List<StubFileElementType>> myFileElementTypesCache = new ConcurrentHashMap<>();
  private final ConcurrentMap<StubFileElementType, Long> myModCounts = new ConcurrentHashMap<>();
  private final NotNullLazyValue<StubUpdatingIndexStorage> myStubUpdatingIndexStorage = NotNullLazyValue.atomicLazy(() -> {
    UpdatableIndex index = ((FileBasedIndexImpl)FileBasedIndex.getInstance()).getIndex(StubUpdatingIndex.INDEX_ID);
    while (index instanceof FileBasedIndexInfrastructureExtensionUpdatableIndex) {
      index = ((FileBasedIndexInfrastructureExtensionUpdatableIndex)index).getBaseIndex();
    }
    return (StubUpdatingIndexStorage)index;
  });
  private final NotNullLazyValue<DataIndexer<Integer, SerializedStubTree, FileContent>> myStubIndexer =
    NotNullLazyValue.atomicLazy(() -> {
      return myStubUpdatingIndexStorage.getValue().getExtension().getIndexer(); // new indexer instance ?????
    });

  private record FileInfo(VirtualFile file, Project project, StubFileElementType type) { }

  private final Queue<VirtualFile> myPendingUpdates = new ArrayDeque<>();
  private final Queue<FileInfo> myProbablyExpensiveUpdates = new ArrayDeque<>();
  private final Set<StubFileElementType<?>> myModificationsInCurrentBatch = new HashSet<>();

  private void registerModificationFor(@NotNull StubFileElementType<?> fileElementType) {
    myModificationsInCurrentBatch.add(fileElementType);
    myModCounts.compute(fileElementType, (__, value) -> {
      if (value == null) return 1L;
      return value + 1;
    });
  }

  private boolean wereModificationsInCurrentBatch(@NotNull StubFileElementType<?> fileElementType) {
    return myModificationsInCurrentBatch.contains(fileElementType);
  }

  public Long getModificationStamp(@NotNull StubFileElementType<?> fileElementType) {
    return myModCounts.getOrDefault(fileElementType, 0L);
  }

  @Override
  public synchronized void processUpdate(@NotNull VirtualFile file) {
    myPendingUpdates.add(file);
  }

  @Override
  public synchronized void endUpdatesBatch() {
    myModificationsInCurrentBatch.clear();
    fastCheck();
    if (myProbablyExpensiveUpdates.size() > PRECISE_CHECK_THRESHOLD) {
      coarseCheck();
    } else {
      preciseCheck();
    }
  }

  private void fastCheck() {
    while (!myPendingUpdates.isEmpty()) {
      VirtualFile file = myPendingUpdates.poll();
      Project project = ProjectLocator.getInstance().guessProjectForFile(file);
      if (project != null && project.isDisposed()) continue;
      IndexedFile indexedFile = new IndexedFileImpl(file, project);
      var current = determineCurrentFileElementType(indexedFile);
      var beforeSuitableTypes = determinePreviousFileElementType(FileBasedIndex.getFileId(file), myStubUpdatingIndexStorage.get());
      if (beforeSuitableTypes.size() > 1) {
        for (var type : beforeSuitableTypes) {
          registerModificationFor(type);
        }
        if (current != null) registerModificationFor(current);
        continue;
      }
      var before = beforeSuitableTypes.isEmpty() ? null : beforeSuitableTypes.get(0);
      if (current != before) {
        if (current != null) registerModificationFor(current);
        if (before != null) registerModificationFor(before);
      }
      else {
        if (current != null) myProbablyExpensiveUpdates.add(new FileInfo(file, project, current));
      }
    }
  }

  private void coarseCheck() {
    while (!myProbablyExpensiveUpdates.isEmpty()) {
      FileInfo info = myProbablyExpensiveUpdates.poll();
      if (wereModificationsInCurrentBatch(info.type)) continue;
      registerModificationFor(info.type);
    }
  }

  private void preciseCheck() {
    DataIndexer<Integer, SerializedStubTree, FileContent> stubIndexer = myStubIndexer.getValue();
    while (!myProbablyExpensiveUpdates.isEmpty()) {
      FileInfo info = myProbablyExpensiveUpdates.poll();
      if (wereModificationsInCurrentBatch(info.type) ||
          (info.project != null && info.project.isDisposed())) continue;
      try {
        var diffBuilder = (StubCumulativeInputDiffBuilder)myStubUpdatingIndexStorage.getValue().getForwardIndexAccessor()
          .getDiffBuilder(
            FileBasedIndex.getFileId(info.file),
            null // see SingleEntryIndexForwardIndexAccessor#getDiffBuilder
          );
        FileContent fileContent = FileContentImpl.createByFile(info.file, info.project);
        Stub stub;
        try {
          FileBasedIndexImpl.markFileIndexed(info.file, fileContent);
          stub = StubTreeBuilder.buildStubTree(fileContent);
        } finally {
          FileBasedIndexImpl.unmarkBeingIndexed();
        }
        Map<Integer, SerializedStubTree> serializedStub = stub == null ? Collections.emptyMap() : stubIndexer.map(fileContent);
        if (diffBuilder.differentiate(serializedStub, (__, ___, ____) -> { }, (__, ___, ____) -> { }, (__, ___) -> { }, true)) {
          registerModificationFor(info.type);
        }
      }
      catch (IOException | StorageException e) {
        LOG.error(e);
      }
    }
  }

  public void dispose() {
    myFileElementTypesCache.clear();
    myModCounts.clear();
    myPendingUpdates.clear();
    myProbablyExpensiveUpdates.clear();
    myModificationsInCurrentBatch.clear();
  }

  private static @Nullable StubFileElementType determineCurrentFileElementType(IndexedFile indexedFile) {
    if (shouldSkipFile(indexedFile)) return null;
    var stubBuilderType = StubTreeBuilder.getStubBuilderType(indexedFile, true);
    if (stubBuilderType == null) return null;
    return stubBuilderType.getStubFileElementType();
  }

  private @NotNull List<StubFileElementType> determinePreviousFileElementType(int fileId, StubUpdatingIndexStorage index) {
    String storedVersion = index.getStoredSubIndexerVersion(fileId);
    if (storedVersion == null) return Collections.emptyList();
    return myFileElementTypesCache.compute(storedVersion, (__, value) -> {
      if (value != null) return value;
      List<StubFileElementType> types = StubBuilderType.getStubFileElementTypeFromVersion(storedVersion);
      if (types.size() > 1) {
        LOG.error("Cannot distinguish StubFileElementTypes. This might worsen the performance. Version: " + storedVersion + " -> " +
                  ContainerUtil.map(types, t -> {
                    return t.getClass().getName() + "{" + t.getExternalId() + ";" + t.getDebugName() + "}";
                  }));
      }
      return types;
    });
  }

  /**
   * There is no need to process binary files (e.g. .class), so we should just skip them.
   * Their processing might trigger content read which is expensive
   */
  private static boolean shouldSkipFile(IndexedFile indexedFile) {
    FileType fileType = indexedFile.getFileType(); // this code snippet is taken from StubTreeBuilder#getStubBuilderType
    final BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
    return builder != null;
  }
}
