// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.impl.CachedFileType;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.util.BooleanFunction;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.projectFilter.FileAddStatus;
import com.intellij.util.indexing.projectFilter.ProjectIndexableFilesFilterHolder;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

final class UnindexedFilesFinder {
  private static final Logger LOG = Logger.getInstance(UnindexedFilesFinder.class);

  private final Project myProject;
  private final FileBasedIndexImpl myFileBasedIndex;
  private final UpdatableIndex<FileType, Void, FileContent, ?> myFileTypeIndex;
  private final Collection<FileBasedIndexInfrastructureExtension.FileIndexingStatusProcessor> myStateProcessors;
  private final @Nullable BooleanFunction<? super IndexedFile> myForceReindexingTrigger;
  private final @NotNull ProjectIndexableFilesFilterHolder myIndexableFilesFilterHolder;
  private final boolean myShouldProcessUpToDateFiles;

  private static class UnindexedFileStatusBuilder {
    boolean shouldIndex = false;
    boolean indexesWereProvidedByInfrastructureExtension = false;
    long timeProcessingUpToDateFiles = 0;
    long timeUpdatingContentLessIndexes = 0;
    long timeIndexingWithoutContentViaInfrastructureExtension = 0;
    private final List<Computable<Boolean>> appliersAndRemovers;
    final boolean applyIndexValuesSeparately;
    boolean indexInfrastructureExtensionInvalidated = false;

    UnindexedFileStatusBuilder(boolean applyIndexValuesSeparately) {
      this.applyIndexValuesSeparately = applyIndexValuesSeparately;
      appliersAndRemovers = applyIndexValuesSeparately ? new SmartList<>() : Collections.emptyList();
    }

    boolean addOrRunApplierOrRemover(@Nullable Computable<Boolean> applierOrRemover) {
      if (applierOrRemover == null) return true;

      if (applyIndexValuesSeparately) {
        return appliersAndRemovers.add(applierOrRemover);
      }
      else {
        return applierOrRemover.compute();
      }
    }

    @Contract(" -> new")
    @NotNull UnindexedFileStatus build() {
      return new UnindexedFileStatus(shouldIndex,
                                     indexesWereProvidedByInfrastructureExtension,
                                     timeProcessingUpToDateFiles,
                                     timeUpdatingContentLessIndexes,
                                     timeIndexingWithoutContentViaInfrastructureExtension);
    }
  }

  UnindexedFilesFinder(@NotNull Project project,
                       @NotNull FileBasedIndexImpl fileBasedIndex,
                       @Nullable BooleanFunction<? super IndexedFile> forceReindexingTrigger,
                       @Nullable VirtualFile root) {
    myProject = project;
    myFileBasedIndex = fileBasedIndex;
    myFileTypeIndex = fileBasedIndex.getIndex(FileTypeIndex.NAME);

    myStateProcessors = FileBasedIndexInfrastructureExtension.EP_NAME.getExtensionList().stream()
      .map(ex -> ex.createFileIndexingStatusProcessor(project))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
    myForceReindexingTrigger = forceReindexingTrigger;

    myShouldProcessUpToDateFiles = ContainerUtil.find(myStateProcessors, p -> p.shouldProcessUpToDateFiles()) != null;

    myIndexableFilesFilterHolder = fileBasedIndex.getIndexableFilesFilterHolder();
  }

  @Nullable("null if the file is not subject for indexing (a directory, invalid, etc.)")
  public UnindexedFileStatus getFileStatus(@NotNull VirtualFile file) {
    ProgressManager.checkCanceled(); // give a chance to suspend indexing
    if (!file.isValid() || !(file instanceof VirtualFileWithId)) {
      return null;
    }
    Supplier<@NotNull Boolean> checker = CachedFileType.getFileTypeChangeChecker();
    FileType cachedFileType = file.getFileType();
    boolean applyIndexValuesSeparately = FileBasedIndexImpl.isWritingIndexValuesSeparatedFromCountingForContentIndependentIndexes();
    return ReadAction.compute(() -> {
      if (myProject.isDisposed() || !file.isValid()) {
        return null;
      }
      FileType fileType = checker.get() ? cachedFileType : null;

      UnindexedFileStatusBuilder fileStatusBuilder = new UnindexedFileStatusBuilder(applyIndexValuesSeparately);

      IndexedFileImpl indexedFile = new IndexedFileImpl(file, fileType, myProject);
      int inputId = FileBasedIndex.getFileId(file);
      boolean fileWereJustAdded = myIndexableFilesFilterHolder.addFileId(inputId, myProject) == FileAddStatus.ADDED;

      if (file instanceof VirtualFileSystemEntry && ((VirtualFileSystemEntry)file).isFileIndexed()) {
        boolean wasInvalidated = false;
        if (fileWereJustAdded) {
          List<ID<?, ?>> ids = IndexingStamp.getNontrivialFileIndexedStates(inputId);
          for (FileBasedIndexInfrastructureExtension.FileIndexingStatusProcessor processor : myStateProcessors) {
            for (ID<?, ?> id : ids) {
              if (myFileBasedIndex.needsFileContentLoading(id)) {
                long nowTime = System.nanoTime();
                try {
                  if (!processor.processUpToDateFile(indexedFile, inputId, id)) {
                    wasInvalidated = true;
                  }
                }
                finally {
                  fileStatusBuilder.timeProcessingUpToDateFiles += (System.nanoTime() - nowTime);
                }
              }
            }
          }
        }
        if (!wasInvalidated) {
          IndexingStamp.flushCache(inputId);
          return fileStatusBuilder.build();
        }
      }

      FileTypeManagerEx ex = FileTypeManagerEx.getInstanceEx();
      if (!(ex instanceof FileTypeManagerImpl)) {
        return fileStatusBuilder.build();
      }
      Ref<Runnable> finalization = new Ref<>();
      ((FileTypeManagerImpl)ex).freezeFileTypeTemporarilyWithProvidedValueIn(file, fileType, () -> {
        boolean isDirectory = file.isDirectory();
        FileIndexingState fileTypeIndexState = null;
        boolean needContentIndexing = !isDirectory && !myFileBasedIndex.isTooLarge(file);
        if (needContentIndexing) {
          if ((fileTypeIndexState = myFileTypeIndex.getIndexingStateForFile(inputId, indexedFile)) == FileIndexingState.OUT_DATED) {
            if (myFileBasedIndex.doTraceIndexUpdates()) {
              LOG.info("Scheduling full indexing of " + indexedFile.getFileName() + " because file type index is outdated");
            }
            myFileBasedIndex.dropNontrivialIndexedStates(inputId);
            fileStatusBuilder.shouldIndex = true;
          }
          else {
            List<ID<?, ?>> affectedContentIndexCandidates = new ArrayList<>();
            for (ID<?, ?> candidate : myFileBasedIndex.getAffectedIndexCandidates(indexedFile)) {
              if (myFileBasedIndex.needsFileContentLoading(candidate)) affectedContentIndexCandidates.add(candidate);
            }

            for (ID<?, ?> indexId : affectedContentIndexCandidates) {
              if (FileBasedIndexScanUtil.isManuallyManaged(indexId)) continue;
              try {
                FileIndexingState fileIndexingState = myFileBasedIndex.shouldIndexFile(indexedFile, indexId);
                if (fileIndexingState == FileIndexingState.UP_TO_DATE && myShouldProcessUpToDateFiles) {
                  fileIndexingState = processUpToDateFileByInfrastructureExtensions(indexedFile, inputId, indexId, fileStatusBuilder);
                }
                if (fileIndexingState.updateRequired()) {
                  if (myFileBasedIndex.doTraceStubUpdates(indexId)) {
                    FileBasedIndexImpl.LOG.info(
                      "Scheduling indexing of " + indexedFile.getFileName() + " by request of index; " + indexId +
                      (fileStatusBuilder.indexInfrastructureExtensionInvalidated ? " because extension invalidated;" : "") +
                      ((myFileBasedIndex.acceptsInput(indexId, indexedFile)) ? " accepted;" : " unaccepted;") +
                      ("indexing state = " + myFileBasedIndex.getIndexingState(indexedFile, indexId)));
                  }

                  if (myFileBasedIndex.acceptsInput(indexId, indexedFile)) {
                    if (!tryIndexWithoutContent(indexedFile, inputId, indexId, fileStatusBuilder)) {
                      // NOTE! Do not break the loop here. We must process ALL IDs and pass them to the FileIndexingStatusProcessor
                      // so that it can invalidate all "indexing states" (by means of clearing IndexingStamp)
                      // for all indexes that became invalid. See IDEA-252846 for more details.
                      fileStatusBuilder.shouldIndex = true;
                    }
                  }
                  else {
                    boolean removed = removeIndexedValue(indexedFile, inputId, indexId, fileStatusBuilder);
                    LOG.assertTrue(removed, "Failed to remove value from content index");
                  }
                }
              }
              catch (RuntimeException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof IOException || cause instanceof StorageException) {
                  LOG.info(e);
                  myFileBasedIndex.requestRebuild(indexId);
                }
                else {
                  throw e;
                }
              }
            }
          }
        }

        boolean mayMarkFileIndexed = true;
        long nowTime = System.nanoTime();
        try {
          for (ID<?, ?> indexId : myFileBasedIndex.getContentLessIndexes(isDirectory)) {
            if (!RebuildStatus.isOk(indexId)) {
              mayMarkFileIndexed = false;
              continue;
            }
            if (FileTypeIndex.NAME.equals(indexId) && fileTypeIndexState != null && !fileTypeIndexState.updateRequired()) {
              continue;
            }
            if (myFileBasedIndex.shouldIndexFile(indexedFile, indexId).updateRequired()) {
              if (myFileBasedIndex.acceptsInput(indexId, indexedFile)) {
                boolean indexed = tryIndexWithoutContent(indexedFile, inputId, indexId, fileStatusBuilder);
                LOG.assertTrue(indexed, "Failed to apply contentless indexer");
              } else {
                boolean removed = removeIndexedValue(indexedFile, inputId, indexId, fileStatusBuilder);
                LOG.assertTrue(removed, "Failed to remove value from contentless index");
              }
            }
          }
        }
        finally {
          fileStatusBuilder.timeUpdatingContentLessIndexes += (System.nanoTime() - nowTime);
        }

        if (fileStatusBuilder.appliersAndRemovers.isEmpty()) {
          finishGettingStatus(file, indexedFile, inputId, fileStatusBuilder, mayMarkFileIndexed);
          finalization.set(EmptyRunnable.getInstance());
        }
        else {
          boolean finalMayMarkFileIndexed = mayMarkFileIndexed;
          finalization.set(() -> {
            long applyingStart = System.nanoTime();
            try {
              for (Computable<Boolean> applierOrRemover : fileStatusBuilder.appliersAndRemovers) {
                applierOrRemover.compute();
              }
            }
            finally {
              fileStatusBuilder.timeUpdatingContentLessIndexes += (System.nanoTime() - applyingStart);
            }
            finishGettingStatus(file, indexedFile, inputId, fileStatusBuilder, finalMayMarkFileIndexed);
          });
        }
      });

      finalization.get().run();
      return fileStatusBuilder.build();
    });
  }

  private FileIndexingState processUpToDateFileByInfrastructureExtensions(IndexedFileImpl indexedFile,
                                                                          int inputId,
                                                                          ID<?, ?> indexId,
                                                                          UnindexedFileStatusBuilder fileStatusBuilder) {
    long nowTime = System.nanoTime();
    try {
      FileIndexingState ret = FileIndexingState.UP_TO_DATE;
      for (FileBasedIndexInfrastructureExtension.FileIndexingStatusProcessor p : myStateProcessors) {
        if (!p.processUpToDateFile(indexedFile, inputId, indexId)) {
          fileStatusBuilder.indexInfrastructureExtensionInvalidated = true;
        }
      }
      if (fileStatusBuilder.indexInfrastructureExtensionInvalidated) {
        ret = myFileBasedIndex.shouldIndexFile(indexedFile, indexId);
      }
      return ret;
    }
    finally {
      fileStatusBuilder.timeProcessingUpToDateFiles += (System.nanoTime() - nowTime);
    }
  }

  private boolean removeIndexedValue(IndexedFileImpl indexedFile,
                                     int inputId,
                                     ID<?, ?> indexId,
                                     UnindexedFileStatusBuilder fileStatusBuilder) {

    SingleIndexValueRemover remover =
      myFileBasedIndex.createSingleIndexRemover(indexId, indexedFile.getFile(), new IndexedFileWrapper(indexedFile), inputId,
                                                fileStatusBuilder.applyIndexValuesSeparately);
    if (remover != null) {
      return fileStatusBuilder.addOrRunApplierOrRemover(remover::remove);
    }
    else {
      return true;
    }
  }

  private boolean tryIndexWithoutContent(IndexedFileImpl indexedFile,
                                         int inputId,
                                         ID<?, ?> indexId,
                                         UnindexedFileStatusBuilder fileStatusBuilder) {
    if (myFileBasedIndex.needsFileContentLoading(indexId)) {
      long nowTime = System.nanoTime();
      boolean wasIndexedByInfrastructure;
      try {
        wasIndexedByInfrastructure = tryIndexWithoutContentViaInfrastructureExtension(indexedFile, inputId, indexId);
      }
      finally {
        fileStatusBuilder.timeIndexingWithoutContentViaInfrastructureExtension += (System.nanoTime() - nowTime);
      }
      if (wasIndexedByInfrastructure) {
        fileStatusBuilder.indexesWereProvidedByInfrastructureExtension = true;
        return true;
      }
      else {
        return false;
      }
    }
    else {
      SingleIndexValueApplier<?> applier =
        myFileBasedIndex.createSingleIndexValueApplier(indexId, indexedFile.getFile(), inputId, new IndexedFileWrapper(indexedFile),
                                                       fileStatusBuilder.applyIndexValuesSeparately);
      if (applier != null) {
        return fileStatusBuilder.addOrRunApplierOrRemover(applier::apply);
      }
      else {
        return true;
      }
    }
  }

  private void finishGettingStatus(@NotNull VirtualFile file,
                                   IndexedFileImpl indexedFile,
                                   int inputId,
                                   UnindexedFileStatusBuilder fileStatusBuilder,
                                   boolean mayMarkFileIndexed) {
    if (myForceReindexingTrigger != null && myForceReindexingTrigger.fun(indexedFile)) {
      myFileBasedIndex.dropNontrivialIndexedStates(inputId);
      fileStatusBuilder.shouldIndex = true;
    }

    IndexingStamp.flushCache(inputId);
    if (!fileStatusBuilder.shouldIndex && mayMarkFileIndexed) {
      IndexingFlag.setFileIndexed(file);
    }
  }

  private boolean tryIndexWithoutContentViaInfrastructureExtension(IndexedFile fileContent, int inputId, ID<?, ?> indexId) {
    for (FileBasedIndexInfrastructureExtension.FileIndexingStatusProcessor processor : myStateProcessors) {
      if (processor.tryIndexFileWithoutContent(fileContent, inputId, indexId)) {
        FileBasedIndexImpl.setIndexedState(myFileBasedIndex.getIndex(indexId), fileContent, inputId, true);
        if (myFileBasedIndex.doTraceStubUpdates(indexId)) {
          LOG.info("File " + fileContent.getFileName() + " indexed using extension for " + indexId + " without content");
        }
        return true;
      }
    }
    return false;
  }
}