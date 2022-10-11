// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.util.indexing.IndexedFile;
import com.intellij.util.indexing.IndexedFileImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class PerFileElementTypeStubModificationTracker {
  private final ConcurrentMap<String, Ref<Class<? extends IFileElementType>>> // ref is to store nulls
    myFileElementTypesCache = new ConcurrentHashMap<>();
  private final ConcurrentMap<Class<? extends IFileElementType>, Long> myModCounts = new ConcurrentHashMap<>();
  private final NotNullLazyValue<StubUpdatingIndexStorage> myStubUpdatingIndexStorage = NotNullLazyValue.atomicLazy(() -> {
    return (StubUpdatingIndexStorage)((FileBasedIndexImpl)FileBasedIndex.getInstance()).getIndex(StubUpdatingIndex.INDEX_ID);
  });

  private void incModCount(@NotNull Class<? extends IFileElementType> fileElementType) {
    myModCounts.compute(fileElementType, (__, value) -> {
      if (value == null) return 1L;
      return value + 1;
    });
  }

  public Long getModificationStamp(@NotNull Class<? extends IFileElementType> fileElementType) {
    return myModCounts.getOrDefault(fileElementType, 0L);
  }

  public void processFileElementTypeUpdate(@NotNull VirtualFile file) {
    IndexedFile indexedFile = new IndexedFileImpl(file, ProjectLocator.getInstance().guessProjectForFile(file));
    var current = determineCurrentFileElementType(indexedFile);
    if (current != null) {
      incModCount(current);
    }
    int fileId = FileBasedIndex.getFileId(file);
    var before = determinePreviousFileElementTypePrecisely(fileId, myStubUpdatingIndexStorage.get());
    if (before != null && before != current) {
      incModCount(before);
    }
  }

  public void dispose() {
    myFileElementTypesCache.clear();
    myModCounts.clear();
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
