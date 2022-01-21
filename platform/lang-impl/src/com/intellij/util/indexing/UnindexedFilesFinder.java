// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.util.BooleanFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.projectFilter.FileAddStatus;
import com.intellij.util.indexing.projectFilter.ProjectIndexableFilesFilterHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

final class UnindexedFilesFinder {
  private static final Logger LOG = Logger.getInstance(UnindexedFilesFinder.class);

  private final Project myProject;
  private final FileBasedIndexImpl myFileBasedIndex;
  private final UpdatableIndex<FileType, Void, FileContent> myFileTypeIndex;
  private final Collection<FileBasedIndexInfrastructureExtension.FileIndexingStatusProcessor> myStateProcessors;
  private final @Nullable BooleanFunction<? super IndexedFile> myForceReindexingTrigger;
  private final @NotNull ProjectIndexableFilesFilterHolder myIndexableFilesFilterHolder;
  private final boolean myShouldProcessUpToDateFiles;

  UnindexedFilesFinder(@NotNull Project project,
                       @NotNull FileBasedIndexImpl fileBasedIndex,
                       @Nullable BooleanFunction<? super IndexedFile> forceReindexingTrigger) {
    myProject = project;
    myFileBasedIndex = fileBasedIndex;
    myFileTypeIndex = fileBasedIndex.getIndex(FileTypeIndex.NAME);

    myStateProcessors = FileBasedIndexInfrastructureExtension
      .EP_NAME
      .extensions()
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
    return ReadAction.compute(() -> {
      if (myProject.isDisposed() || !file.isValid() || !(file instanceof VirtualFileWithId)) {
        return null;
      }

      AtomicBoolean indexesWereProvidedByInfrastructureExtension = new AtomicBoolean();
      AtomicLong timeProcessingUpToDateFiles = new AtomicLong();
      AtomicLong timeUpdatingContentLessIndexes = new AtomicLong();
      AtomicLong timeIndexingWithoutContent = new AtomicLong();

      IndexedFileImpl indexedFile = new IndexedFileImpl(file, myProject);
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
                } finally {
                  timeProcessingUpToDateFiles.addAndGet(System.nanoTime() - nowTime);
                }
              }
            }
          }
        }
        if (!wasInvalidated) {
          IndexingStamp.flushCache(inputId);
          return new UnindexedFileStatus(false,
                                         false,
                                         timeProcessingUpToDateFiles.get(),
                                         timeUpdatingContentLessIndexes.get(),
                                         timeIndexingWithoutContent.get());
        }
      }

      AtomicBoolean shouldIndex = new AtomicBoolean();

      FileTypeManagerEx.getInstanceEx().freezeFileTypeTemporarilyIn(file, () -> {
        boolean isDirectory = file.isDirectory();
        FileIndexingState fileTypeIndexState = null;
        if (!isDirectory && !myFileBasedIndex.isTooLarge(file)) {
          if ((fileTypeIndexState = myFileTypeIndex.getIndexingStateForFile(inputId, indexedFile)) == FileIndexingState.OUT_DATED) {
            if (myFileBasedIndex.doTraceIndexUpdates()) {
              LOG.info("Scheduling full indexing of " + indexedFile.getFileName() + " because file type index is outdated");
            }
            myFileBasedIndex.dropNontrivialIndexedStates(inputId);
            shouldIndex.set(true);
          }
          else {
            final List<ID<?, ?>> affectedIndexCandidates = myFileBasedIndex.getAffectedIndexCandidates(indexedFile);
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0, size = affectedIndexCandidates.size(); i < size; ++i) {
              final ID<?, ?> indexId = affectedIndexCandidates.get(i);
              try {
                if (myFileBasedIndex.needsFileContentLoading(indexId)) {
                  FileIndexingState fileIndexingState = myFileBasedIndex.shouldIndexFile(indexedFile, indexId);
                  boolean indexInfrastructureExtensionInvalidated = false;
                  if (fileIndexingState == FileIndexingState.UP_TO_DATE && myShouldProcessUpToDateFiles) {
                    for (FileBasedIndexInfrastructureExtension.FileIndexingStatusProcessor p : myStateProcessors) {
                      long nowTime = System.nanoTime();
                      try {
                        if (!p.processUpToDateFile(indexedFile, inputId, indexId)) {
                          indexInfrastructureExtensionInvalidated = true;
                        }
                      }
                      finally {
                        timeProcessingUpToDateFiles.addAndGet(System.nanoTime() - nowTime);
                      }
                    }
                  }
                  if (indexInfrastructureExtensionInvalidated) {
                    fileIndexingState = myFileBasedIndex.shouldIndexFile(indexedFile, indexId);
                  }
                  if (fileIndexingState.updateRequired()) {
                    if (myFileBasedIndex.doTraceStubUpdates(indexId)) {
                      FileBasedIndexImpl.LOG.info("Scheduling indexing of " + indexedFile.getFileName() + " by request of index; " + indexId +
                                                  (indexInfrastructureExtensionInvalidated ? " because extension invalidated;" : "") +
                                                  ((myFileBasedIndex.acceptsInput(indexId, indexedFile)) ? " accepted;" : " unaccepted;") +
                                                  ("indexing state = " + myFileBasedIndex.getIndexingState(indexedFile, indexId)));
                    }

                    long nowTime = System.nanoTime();
                    boolean wasIndexedByInfrastructure;
                    try {
                      wasIndexedByInfrastructure = tryIndexWithoutContentViaInfrastructureExtension(indexedFile, inputId, indexId);
                    } finally {
                      timeIndexingWithoutContent.addAndGet(System.nanoTime() - nowTime);
                    }
                    if (wasIndexedByInfrastructure) {
                      indexesWereProvidedByInfrastructureExtension.set(true);
                    }
                    else {
                      shouldIndex.set(true);
                      // NOTE! Do not break the loop here. We must process ALL IDs and pass them to the FileIndexingStatusProcessor
                      // so that it can invalidate all "indexing states" (by means of clearing IndexingStamp)
                      // for all indexes that became invalid. See IDEA-252846 for more details.
                    }
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
              myFileBasedIndex.updateSingleIndex(indexId, file, inputId, new IndexedFileWrapper(indexedFile));
            }
          }
        } finally {
          timeUpdatingContentLessIndexes.addAndGet(System.nanoTime() - nowTime);
        }

        if (myForceReindexingTrigger != null && myForceReindexingTrigger.fun(indexedFile)) {
          myFileBasedIndex.dropNontrivialIndexedStates(inputId);
          shouldIndex.set(true);
        }

        IndexingStamp.flushCache(inputId);
        if (!shouldIndex.get() && mayMarkFileIndexed) {
          IndexingFlag.setFileIndexed(file);
        }
      });
      return new UnindexedFileStatus(shouldIndex.get(),
                                     indexesWereProvidedByInfrastructureExtension.get(),
                                     timeProcessingUpToDateFiles.get(),
                                     timeUpdatingContentLessIndexes.get(),
                                     timeIndexingWithoutContent.get());
    });
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