// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.util.SystemProperties;
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

  private final ConcurrentMap<String, Ref<Class<? extends IFileElementType>>> // ref is to store nulls
    myFileElementTypesCache = new ConcurrentHashMap<>();
  private final ConcurrentMap<Class<? extends IFileElementType>, Long> myModCounts = new ConcurrentHashMap<>();
  private final NotNullLazyValue<StubUpdatingIndexStorage> myStubUpdatingIndexStorage = NotNullLazyValue.atomicLazy(() -> {
    UpdatableIndex index = ((FileBasedIndexImpl)FileBasedIndex.getInstance()).getIndex(StubUpdatingIndex.INDEX_ID);
    while (index instanceof FileBasedIndexInfrastructureExtensionUpdatableIndex) {
      index = ((FileBasedIndexInfrastructureExtensionUpdatableIndex)index).getBaseIndex();
    }
    return (StubUpdatingIndexStorage)index;
  });

  private record FileInfo(VirtualFile file, Project project, Class<? extends IFileElementType> type) { }

  private final Queue<VirtualFile> myPendingUpdates = new ArrayDeque<>();
  private final Queue<FileInfo> myProbablyExpensiveUpdates = new ArrayDeque<>();
  private final Set<Class<? extends IFileElementType>> myModificationsInCurrentBatch = new HashSet<>();

  private void registerModificationFor(@NotNull Class<? extends IFileElementType> fileElementType) {
    myModificationsInCurrentBatch.add(fileElementType);
    myModCounts.compute(fileElementType, (__, value) -> {
      if (value == null) return 1L;
      return value + 1;
    });
  }

  private boolean wereModificationsInCurrentBatch(@NotNull Class<? extends IFileElementType> fileElementType) {
    return myModificationsInCurrentBatch.contains(fileElementType);
  }

  public Long getModificationStamp(@NotNull Class<? extends IFileElementType> fileElementType) {
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

  private void coarseCheck() {
    while (!myProbablyExpensiveUpdates.isEmpty()) {
      FileInfo info = myProbablyExpensiveUpdates.poll();
      if (wereModificationsInCurrentBatch(info.type)) continue;
      registerModificationFor(info.type);
    }
  }

  private void preciseCheck() {
    DataIndexer<Integer, SerializedStubTree, FileContent> stubIndexer =
      myStubUpdatingIndexStorage.getValue().getExtension().getIndexer(); // new indexer instance ?????
    while (!myProbablyExpensiveUpdates.isEmpty()) {
      FileInfo info = myProbablyExpensiveUpdates.poll();
      if (wereModificationsInCurrentBatch(info.type)) continue;
      try {
        var diffBuilder = (StubCumulativeInputDiffBuilder)myStubUpdatingIndexStorage.getValue().getForwardIndexAccessor()
          .getDiffBuilder(
            FileBasedIndex.getFileId(info.file),
            null // see SingleEntryIndexForwardIndexAccessor#getDiffBuilder
          );
        FileContent fileContent = FileContentImpl.createByFile(info.file, info.project);
        Stub stub = StubTreeBuilder.buildStubTree(fileContent);
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

  private void fastCheck() {
    while (!myPendingUpdates.isEmpty()) {
      VirtualFile file = myPendingUpdates.poll();
      Project project = ProjectLocator.getInstance().guessProjectForFile(file);
      IndexedFile indexedFile = new IndexedFileImpl(file, project);
      var current = determineCurrentFileElementType(indexedFile);
      var before = determinePreviousFileElementTypePrecisely(FileBasedIndex.getFileId(file), myStubUpdatingIndexStorage.get());
      if (current != before) {
        if (current != null) registerModificationFor(current);
        if (before != null) registerModificationFor(before);
      }
      else {
        if (current != null) myProbablyExpensiveUpdates.add(new FileInfo(file, project, current));
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

  @Nullable
  private static Class<? extends IFileElementType> determineCurrentFileElementType(IndexedFile indexedFile) {
    if (shouldSkipFile(indexedFile)) return null;
    var stubBuilderType = StubTreeBuilder.getStubBuilderType(indexedFile, true);
    if (stubBuilderType == null) return null;
    var currentElementType = stubBuilderType.getStubFileElementType();
    return currentElementType == null ? null : currentElementType.getClass();
  }

  @Nullable
  private Class<? extends IFileElementType> determinePreviousFileElementTypePrecisely(int fileId, StubUpdatingIndexStorage index) {
    String storedVersion = index.getStoredSubIndexerVersion(fileId);
    if (storedVersion == null) return null;
    return myFileElementTypesCache.compute(storedVersion, (__, value) -> {
      if (value != null) return value;
      return Ref.create(StubBuilderType.getStubFileElementTypeFromVersion(storedVersion));
    }).get();
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
