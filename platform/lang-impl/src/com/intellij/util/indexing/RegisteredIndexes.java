// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.util.indexing.FileBasedIndexDataInitialization.FileBasedIndexDataInitializationResult;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.progress.util.ProgressIndicatorUtils.awaitWithCheckCanceled;

public final class RegisteredIndexes {
  private final @NotNull FileDocumentManager myFileDocumentManager;
  private final @NotNull FileBasedIndexImpl myFileBasedIndex;
  private final @NotNull Future<FileBasedIndexDataInitializationResult> myStateFuture;

  private final List<ID<?, ?>> myIndicesForDirectories = new CopyOnWriteArrayList<>();

  private final Set<ID<?, ?>> myNotRequiringContentIndices = ConcurrentHashMap.newKeySet();
  private final Set<ID<?, ?>> myRequiringContentIndices = ConcurrentHashMap.newKeySet();
  private final Set<FileType> myNoLimitCheckTypes = ConcurrentHashMap.newKeySet();

  private volatile boolean myExtensionsRelatedDataWasLoaded;

  private volatile boolean myInitialized;

  private volatile FileBasedIndexDataInitializationResult myInitResult;
  private volatile Future<?> myAllIndicesInitializedFuture;

  private final Map<ID<?, ?>, DocumentUpdateTask> myUnsavedDataUpdateTasks = new ConcurrentHashMap<>();

  private final AtomicBoolean myShutdownPerformed = new AtomicBoolean(false);

  private volatile RequiredIndexesEvaluator myRequiredIndexesEvaluator;

  RegisteredIndexes(@NotNull FileDocumentManager fileDocumentManager, @NotNull FileBasedIndexImpl fileBasedIndex) {
    myFileDocumentManager = fileDocumentManager;
    myFileBasedIndex = fileBasedIndex;
    myStateFuture = IndexDataInitializer.submitGenesisTask(fileBasedIndex.coroutineScope,
                                                           new FileBasedIndexDataInitialization(fileBasedIndex, this));
  }

  boolean performShutdown() {
    return myShutdownPerformed.compareAndSet(false, true);
  }

  boolean isShutdownPerformed() {
    return myShutdownPerformed.get();
  }

  void setInitializationResult(@NotNull FileBasedIndexDataInitializationResult result) {
    myInitResult = result;
  }

  IndexConfiguration getState() {
    FileBasedIndexDataInitializationResult result = myInitResult;
    return result == null ? null : result.myState;
  }

  @NotNull
  IndexConfiguration getConfigurationState() {
    return getInitializationResult().myState;
  }

  boolean getWasCorrupted() {
    return getInitializationResult().myWasCorrupted;
  }

  @NotNull
  OrphanDirtyFilesQueue getOrphanDirtyFilesQueue() {
    return getInitializationResult().myOrphanDirtyFilesQueue;
  }

  private @NotNull FileBasedIndexDataInitializationResult getInitializationResult() {
    FileBasedIndexDataInitializationResult result = myInitResult; // memory barrier
    if (result == null) {
      try {
        myInitResult = result = awaitWithCheckCanceled(myStateFuture);
      }
      catch (ProcessCanceledException ex) {
        throw ex;
      }
      catch (Throwable t) {
        throw new RuntimeException(t);
      }
    }
    return result;
  }

  void waitUntilAllIndicesAreInitialized() {
    waitUntilIndicesAreInitialized();
    awaitWithCheckCanceled(myAllIndicesInitializedFuture);
  }

  void waitUntilIndicesAreInitialized() {
    awaitWithCheckCanceled((Future<?>)myStateFuture);
  }

  void extensionsDataWasLoaded() {
    myExtensionsRelatedDataWasLoaded = true;
  }

  void markInitialized() {
    myInitialized = true;
    myRequiredIndexesEvaluator = new RequiredIndexesEvaluator(this);
  }

  void resetHints() {
    myRequiredIndexesEvaluator = new RequiredIndexesEvaluator(this);
  }

  void ensureLoadedIndexesUpToDate() {
    myAllIndicesInitializedFuture = IndexDataInitializer.submitGenesisTask(myFileBasedIndex.coroutineScope, () -> {
      if (!myShutdownPerformed.get()) {
        myFileBasedIndex.ensureStaleIdsDeleted();
        myFileBasedIndex.getChangedFilesCollector().ensureUpToDateAsync();
      }
      return null;
    });
  }

  void registerIndexExtension(@NotNull FileBasedIndexExtension<?, ?> extension) {
    ID<?, ?> name = extension.getName();
    if (extension.dependsOnFileContent()) {
      myUnsavedDataUpdateTasks.put(name, new DocumentUpdateTask(name));
    }

    if (extension.getName() == FilenameIndex.NAME && FileBasedIndexExtension.USE_VFS_FOR_FILENAME_INDEX) {
      return;
    }

    if (!extension.dependsOnFileContent()) {
      if (extension.indexDirectories()) myIndicesForDirectories.add(name);
      myNotRequiringContentIndices.add(name);
    }
    else {
      myRequiringContentIndices.add(name);
    }

    myNoLimitCheckTypes.addAll(extension.getFileTypesWithSizeLimitNotApplicable());
  }

  @NotNull
  Set<FileType> getNoLimitCheckFileTypes() {
    return myNoLimitCheckTypes;
  }

  boolean areIndexesReady() {
    return myStateFuture.isDone() && myAllIndicesInitializedFuture != null && myAllIndicesInitializedFuture.isDone();
  }

  boolean isExtensionsDataLoaded() {
    return myExtensionsRelatedDataWasLoaded;
  }

  public boolean isInitialized() {
    return myInitialized;
  }

  Set<ID<?, ?>> getRequiringContentIndices() {
    return myRequiringContentIndices;
  }

  @NotNull
  Set<ID<?, ?>> getNotRequiringContentIndices() {
    return myNotRequiringContentIndices;
  }

  @NotNull
  List<ID<?, ?>> getIndicesForDirectories() {
    return myIndicesForDirectories;
  }

  public boolean isContentDependentIndex(@NotNull ID<?, ?> indexId) {
    return myRequiringContentIndices.contains(indexId);
  }

  UpdateTask<Document> getUnsavedDataUpdateTask(@NotNull ID<?, ?> indexId) {
    return myUnsavedDataUpdateTasks.get(indexId);
  }

  private final class DocumentUpdateTask extends UpdateTask<Document> {
    private final ID<?, ?> myIndexId;

    DocumentUpdateTask(ID<?, ?> indexId) {
      myIndexId = indexId;
    }

    @Override
    void doProcess(Document document, Project project) {
      myFileBasedIndex.indexUnsavedDocument(document, myIndexId, project, myFileDocumentManager.getFile(document));
    }
  }

  @NotNull
  List<ID<?, ?>> getRequiredIndexes(@NotNull IndexedFile indexedFile) {
    return myRequiredIndexesEvaluator.getRequiredIndexes(indexedFile);
  }

  @TestOnly
  public @NotNull Pair<List<ID<?, ?>>, List<ID<?, ?>>> getRequiredIndexesForFileType(@NotNull FileType fileType) {
    return myRequiredIndexesEvaluator.getRequiredIndexesForFileType(fileType);
  }
}
