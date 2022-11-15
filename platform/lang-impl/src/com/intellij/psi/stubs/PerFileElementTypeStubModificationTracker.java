// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.ClearableLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.StubFileElementType;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class PerFileElementTypeStubModificationTracker implements StubIndexImpl.FileUpdateProcessor {
  static final Logger LOG = Logger.getInstance(PerFileElementTypeStubModificationTracker.class);
  public static final int PRECISE_CHECK_THRESHOLD =
    SystemProperties.getIntProperty("stub.index.per.file.element.type.modification.tracker.precise.check.threshold", 20);

  private final ConcurrentMap<String, List<StubFileElementType>> myFileElementTypesCache = new ConcurrentHashMap<>();
  private final ConcurrentMap<StubFileElementType, Long> myModCounts = new ConcurrentHashMap<>();
  private final ClearableLazyValue<StubUpdatingIndexStorage> myStubUpdatingIndexStorage = ClearableLazyValue.createAtomic(() -> {
    UpdatableIndex index = ((FileBasedIndexImpl)FileBasedIndex.getInstance()).getIndex(StubUpdatingIndex.INDEX_ID);
    while (index instanceof FileBasedIndexInfrastructureExtensionUpdatableIndex) {
      index = ((FileBasedIndexInfrastructureExtensionUpdatableIndex)index).getBaseIndex();
    }
    return (StubUpdatingIndexStorage)index;
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
    ReadAction.run(() -> {
      fastCheck();
      if (myProbablyExpensiveUpdates.size() > PRECISE_CHECK_THRESHOLD) {
        coarseCheck();
      } else {
        preciseCheck();
      }
    });
  }

  private void fastCheck() {
    while (!myPendingUpdates.isEmpty()) {
      VirtualFile file = myPendingUpdates.poll();
      if (file.isDirectory()) continue;
      if (!file.isValid()) {
        // file is deleted or changed externally
        var beforeSuitableTypes = determinePreviousFileElementType(FileBasedIndex.getFileId(file), myStubUpdatingIndexStorage.getValue());
        for (var type : beforeSuitableTypes) {
          registerModificationFor(type);
        }
        continue;
      }
      Project project = ProjectLocator.getInstance().guessProjectForFile(file);
      if (project != null && project.isDisposed()) continue;
      IndexedFile indexedFile = new IndexedFileImpl(file, project);
      var current = determineCurrentFileElementType(indexedFile);
      var beforeSuitableTypes = determinePreviousFileElementType(FileBasedIndex.getFileId(file), myStubUpdatingIndexStorage.getValue());
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
    DataIndexer<Integer, SerializedStubTree, FileContent> stubIndexer = myStubUpdatingIndexStorage.getValue().getIndexer();
    while (!myProbablyExpensiveUpdates.isEmpty()) {
      FileInfo info = myProbablyExpensiveUpdates.poll();
      if (wereModificationsInCurrentBatch(info.type) ||
          (info.project != null && info.project.isDisposed())) continue;
      FileBasedIndexImpl.markFileIndexed(info.file, null);
      try {
        var diffBuilder = (StubCumulativeInputDiffBuilder)myStubUpdatingIndexStorage.getValue().getForwardIndexAccessor()
          .getDiffBuilder(
            FileBasedIndex.getFileId(info.file),
            null // see SingleEntryIndexForwardIndexAccessor#getDiffBuilder
          );
        // file might be deleted from actual fs, but still be "valid" in vfs (e.g. that happen sometimes in tests)
        final FileContent fileContent = getTransientAwareFileContent(info);
        if (fileContent == null) {
          registerModificationFor(info.type);
          continue;
        }
        final Stub stub = StubTreeBuilder.buildStubTree(fileContent);
        Map<Integer, SerializedStubTree> serializedStub = stub == null ? Collections.emptyMap() : stubIndexer.map(fileContent);
        if (diffBuilder.differentiate(serializedStub, (__, ___, ____) -> { }, (__, ___, ____) -> { }, (__, ___) -> { }, true)) {
          registerModificationFor(info.type);
        }
      }
      catch (IOException | StorageException e) {
        LOG.error(e);
      }
      finally {
        FileBasedIndexImpl.unmarkBeingIndexed();
      }
    }
  }

  private static @Nullable FileContent getTransientAwareFileContent(FileInfo info) throws IOException {
    Document doc = FileDocumentManager.getInstance().getDocument(info.file);
    if (doc == null || info.project == null) {
      try {
        return FileContentImpl.createByFile(info.file, info.project);
      } catch (FileNotFoundException ignored) {
        return null;
      }
    }
    PsiFile psi = PsiDocumentManager.getInstance(info.project).getPsiFile(doc);
    DocumentContent content = FileBasedIndexImpl.findLatestContent(doc, psi);
    return FileContentImpl.createByContent(info.file, () -> {
      var text = content.getText();
      return text.toString().getBytes(StandardCharsets.UTF_8);
    }, info.project);
  }

  public void dispose() {
    myFileElementTypesCache.clear();
    myModCounts.clear();
    myPendingUpdates.clear();
    myProbablyExpensiveUpdates.clear();
    myModificationsInCurrentBatch.clear();
    myStubUpdatingIndexStorage.drop();
  }

  private static @Nullable StubFileElementType determineCurrentFileElementType(IndexedFile indexedFile) {
    if (shouldSkipFile(indexedFile.getFile())) return null;
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
        LOG.error("Cannot distinguish StubFileElementTypes. This might worsen the performance. " +
                  "Providing unique externalId or adding a distinctive debugName when instantiating StubFileElementTypes can help. " +
                  "Version: " + storedVersion + " -> " +
                  ContainerUtil.map(types, t -> {
                    return t.getClass().getName() + "{" + t.getExternalId() + ";" + t.getDebugName() + ";" + t.getLanguage() + "}";
                  }));
      }
      return types;
    });
  }

  /**
   * There is no need to process binary files (e.g. .class), so we should just skip them.
   * Their processing might trigger content read which is expensive.
   * Also, we should not process files which weren't indexed by StubIndex yet (or won't be, such as large files).
   */
  private static boolean shouldSkipFile(VirtualFile file) {
    if (((FileBasedIndexImpl)FileBasedIndex.getInstance()).isTooLarge(file)) {
      return true;
    }
    { // this code snippet is taken from StubTreeBuilder#getStubBuilderType
      FileType fileType = file.getFileType();
      final BinaryFileStubBuilder builder = BinaryFileStubBuilders.INSTANCE.forFileType(fileType);
      if (builder != null) return true;
    }
    if (!StubUpdatingIndex.canHaveStub(file)) return true;
    return false;
  }
}
