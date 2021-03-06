// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

final class UnindexedFilesFinder {
  private static final Logger LOG = Logger.getInstance(UnindexedFilesFinder.class);

  private final Project myProject;
  private final boolean myDoTraceForFilesToBeIndexed = FileBasedIndexImpl.LOG.isTraceEnabled();
  private final FileBasedIndexImpl myFileBasedIndex;
  private final UpdatableIndex<FileType, Void, FileContent> myFileTypeIndex;
  private final Collection<FileBasedIndexInfrastructureExtension.FileIndexingStatusProcessor> myStateProcessors;
  private final boolean myRunExtensionsForFilesMarkedAsIndexed;
  private final boolean myShouldProcessUpToDateFiles;

  UnindexedFilesFinder(@NotNull Project project,
                       @NotNull FileBasedIndexImpl fileBasedIndex,
                       boolean runExtensionsForFilesMarkedAsIndexed) {
    myProject = project;
    myFileBasedIndex = fileBasedIndex;
    myFileTypeIndex = fileBasedIndex.getIndex(FileTypeIndex.NAME);
    myRunExtensionsForFilesMarkedAsIndexed = runExtensionsForFilesMarkedAsIndexed;

    myStateProcessors = FileBasedIndexInfrastructureExtension
      .EP_NAME
      .extensions()
      .map(ex -> ex.createFileIndexingStatusProcessor(project))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());

    myShouldProcessUpToDateFiles = ContainerUtil.find(myStateProcessors, p -> p.shouldProcessUpToDateFiles()) != null;
  }

  @Nullable("null if the file is not subject for indexing (a directory, invalid, etc.)")
  public UnindexedFileStatus getFileStatus(@NotNull VirtualFile file) {
    return ReadAction.compute(() -> {
      if (myProject.isDisposed() || !file.isValid() || !(file instanceof VirtualFileWithId)) {
        return null;
      }

      AtomicBoolean indexesWereProvidedByInfrastructureExtension = new AtomicBoolean();
      AtomicLong timeProcessingUpToDateFiles = new AtomicLong();
      AtomicLong timeUpdatingContentLessIndexes = new AtomicLong();
      AtomicLong timeIndexingWithoutContent = new AtomicLong();

      IndexedFileImpl indexedFile = new IndexedFileImpl(file, myProject);
      if (file instanceof VirtualFileSystemEntry && ((VirtualFileSystemEntry)file).isFileIndexed()) {
        int inputId = FileBasedIndex.getFileId(file);

        boolean wasInvalidated = false;
        if (myRunExtensionsForFilesMarkedAsIndexed && myShouldProcessUpToDateFiles) {
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
        int inputId = FileBasedIndex.getFileId(file);
        FileIndexingState fileTypeIndexState = null;
        if (!isDirectory && !myFileBasedIndex.isTooLarge(file)) {
          if ((fileTypeIndexState = myFileTypeIndex.getIndexingStateForFile(inputId, indexedFile)) == FileIndexingState.OUT_DATED) {
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
                  if (fileIndexingState == FileIndexingState.UP_TO_DATE) {
                    if (myShouldProcessUpToDateFiles) {
                      for (FileBasedIndexInfrastructureExtension.FileIndexingStatusProcessor p : myStateProcessors) {
                        long nowTime = System.nanoTime();
                        try {
                          if (!p.processUpToDateFile(indexedFile, inputId, indexId)) {
                            indexInfrastructureExtensionInvalidated = true;
                          }
                        } finally {
                          timeProcessingUpToDateFiles.addAndGet(System.nanoTime() - nowTime);
                        }
                      }
                    }
                  }
                  if (indexInfrastructureExtensionInvalidated) {
                    fileIndexingState = myFileBasedIndex.shouldIndexFile(indexedFile, indexId);
                  }
                  if (fileIndexingState.updateRequired()) {
                    if (myDoTraceForFilesToBeIndexed) {
                      LOG.trace("Scheduling indexing of " + file + " by request of index " + indexId);
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

        long nowTime = System.nanoTime();
        try {
          for (ID<?, ?> indexId : myFileBasedIndex.getContentLessIndexes(isDirectory)) {
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
        IndexingStamp.flushCache(inputId);

        if (!shouldIndex.get()) {
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
        return true;
      }
    }
    return false;
  }
}