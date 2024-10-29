// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.impl.CachedFileType;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.dependencies.FileIndexingStamp;
import com.intellij.util.indexing.dependencies.ScanningRequestToken;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;


final class UnindexedFilesFinder {
  private static final Logger LOG = Logger.getInstance(UnindexedFilesFinder.class);
  private static final boolean TRUST_INDEXING_FLAG = Registry.is("scanning.trust.indexing.flag", true);

  private final Project myProject;
  private final FileBasedIndexImpl myFileBasedIndex;
  private final UpdatableIndex<FileType, Void, FileContent, ?> myFileTypeIndex;
  private final Collection<FileBasedIndexInfrastructureExtension.FileIndexingStatusProcessor> myStateProcessors;
  private final @Nullable BiPredicate<? super IndexedFile, ? super FileIndexingStamp> myForceReindexingTrigger;
  private final FilesFilterScanningHandler myFilterHandler;
  private final boolean myShouldProcessUpToDateFiles;
  private final IndexingReasonExplanationLogger explanationLogger;
  private final ScanningRequestToken indexingRequest;

  private static final class UnindexedFileStatusBuilder {
    boolean shouldIndex = false;
    boolean indexesWereProvidedByInfrastructureExtension = false;
    long timeProcessingUpToDateFiles = 0;
    long timeUpdatingContentLessIndexes = 0;
    long timeIndexingWithoutContentViaInfrastructureExtension = 0;
    private @NotNull List<SingleIndexValueApplier<?>> appliers = Collections.emptyList();
    private @NotNull List<SingleIndexValueRemover> removers = Collections.emptyList();
    final @NotNull FileIndexingResult.ApplicationMode applicationMode;
    boolean indexInfrastructureExtensionInvalidated = false;
    boolean mayMarkFileIndexed = true;
    @Nullable ArrayList<Pair<FileIndexingState, ID<?, ?>>> unindexedStates;

    UnindexedFileStatusBuilder(@NotNull FileIndexingResult.ApplicationMode applicationMode) {
      this.applicationMode = applicationMode;
    }

    boolean addOrRunRemover(@Nullable SingleIndexValueRemover remover) {
      if (remover == null) return true;

      if (removers.isEmpty()) removers = new SmartList<>();
      return removers.add(remover);
    }

    boolean addOrRunApplier(@Nullable SingleIndexValueApplier<?> applier) {
      if (applier == null) return true;

      if (appliers.isEmpty()) appliers = new SmartList<>();
      return appliers.add(applier);
    }

    void addUnindexedState(FileIndexingState state, ID<?, ?> id) {
      if (unindexedStates == null) unindexedStates = new ArrayList<>();
      unindexedStates.add(new Pair<>(state, id));
    }

    @Contract(" -> new")
    @NotNull UnindexedFileStatus build() {
      return new UnindexedFileStatus(shouldIndex,
                                     indexesWereProvidedByInfrastructureExtension,
                                     timeProcessingUpToDateFiles,
                                     timeUpdatingContentLessIndexes,
                                     timeIndexingWithoutContentViaInfrastructureExtension);
    }

    void explain(IndexedFileImpl indexedFile, IndexingReasonExplanationLogger logger) {
      if (shouldIndex) {
        logger.logFileIndexingReason(indexedFile, this::getIndexingReasonLogString);
      }
      else if (!appliers.isEmpty() && !removers.isEmpty()) {
        logger.logScannerAppliersAndRemoversForFile(indexedFile, this::getAppliersAndRemoversLogString);
      }
      else if (!appliers.isEmpty()) {
        logger.logScannerAppliersOnlyForFile(indexedFile, this::getAppliersAndRemoversLogString);
      }
      else if (!removers.isEmpty()) {
        logger.logScannerRemoversOnlyForFile(indexedFile, this::getAppliersAndRemoversLogString);
      }
    }

    boolean hasAppliersOrRemovers() {
      return !appliers.isEmpty() || !removers.isEmpty();
    }

    private @NotNull String getAppliersAndRemoversLogString(IndexedFile indexedFile) {
      return "Scanner has updated file " + indexedFile.getFileName() +
             " with appliers: " + appliers +
             " and removers: " + removers + "; ";
    }

    private String getIndexingReasonLogString(IndexedFile indexedFile) {
      StringBuilder sb = new StringBuilder("Scheduling indexing of ");
      sb.append(indexedFile.getFileName());
      sb.append(" by request of indexes: [");
      if (unindexedStates != null) {
        for (Pair<FileIndexingState, ID<?, ?>> state : unindexedStates) {
          sb.append(state.second).append("->").append(state.first).append(",");
        }
      }
      sb.append("]. ");
      if (indexInfrastructureExtensionInvalidated) {
        sb.append("because extension invalidated; ");
      }

      if (hasAppliersOrRemovers()) {
        sb.append(getAppliersAndRemoversLogString(indexedFile));
      }
      return sb.toString();
    }
  }

  UnindexedFilesFinder(@NotNull Project project,
                       IndexingReasonExplanationLogger explanationLogger,
                       @Nullable BiPredicate<? super IndexedFile, ? super FileIndexingStamp> forceReindexingTrigger,
                       ScanningRequestToken indexingRequest,
                       @NotNull FilesFilterScanningHandler filterHandler) {
    this.explanationLogger = explanationLogger;
    myProject = project;
    myFileBasedIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
    myFileTypeIndex = myFileBasedIndex.getIndex(FileTypeIndex.NAME);

    myStateProcessors = FileBasedIndexInfrastructureExtension.EP_NAME.getExtensionList().stream()
      .map(ex -> ex.createFileIndexingStatusProcessor(project))
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
    myForceReindexingTrigger = forceReindexingTrigger;

    myShouldProcessUpToDateFiles = ContainerUtil.find(myStateProcessors, p -> p.shouldProcessUpToDateFiles()) != null;

    myFilterHandler = filterHandler;
    this.indexingRequest = indexingRequest;
  }

  public @Nullable("null if the file is not subject for indexing (a directory, invalid, etc.)") UnindexedFileStatus getFileStatus(@NotNull VirtualFile file) {
    ProgressManager.checkCanceled(); // give a chance to suspend indexing
    if (!file.isValid() || !(file instanceof VirtualFileWithId)) {
      return null;
    }
    // snapshot at the beginning: if file changes while being processed, we can detect this on the following scanning
    FileIndexingStamp indexingStamp = indexingRequest.getFileIndexingStamp(file);
    FileIndexingResult.ApplicationMode applicationMode = FileBasedIndexImpl.getContentIndependentIndexesApplicationMode();

    if (TRUST_INDEXING_FLAG) {
      if (IndexingFlag.isFileIndexed(file, indexingStamp)) {
        myFilterHandler.addFileId(myProject, FileBasedIndex.getFileId(file));
        return new UnindexedFileStatusBuilder(applicationMode).build();
      }
    }

    Supplier<@NotNull Boolean> checker = CachedFileType.getFileTypeChangeChecker();
    FileType cachedFileType = file.getFileType();
    return ReadAction.compute(() -> {
      if (myProject.isDisposed() || !file.isValid()) {
        return null;
      }
      FileType fileType = checker.get() ? cachedFileType : null;

      UnindexedFileStatusBuilder fileStatusBuilder = new UnindexedFileStatusBuilder(applicationMode);

      IndexedFileImpl indexedFile = new IndexedFileImpl(file, fileType, myProject);
      int inputId = FileBasedIndex.getFileId(file);
      myFilterHandler.addFileId(myProject, inputId);

      if (IndexingFlag.isFileIndexed(file, indexingStamp)) {
        boolean wasInvalidated = false;
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
        boolean shouldCheckContentIndexes;
        if (!isDirectory && !myFileBasedIndex.isTooLarge(file)) {
          if ((fileTypeIndexState = myFileBasedIndex.getIndexingState(indexedFile, myFileTypeIndex, indexingStamp)) == FileIndexingState.OUT_DATED) {
            if (FileBasedIndexEx.doTraceIndexUpdates()) {
              LOG.info("Scheduling full indexing of " + indexedFile.getFileName() + " because file type index is outdated");
            }
            myFileBasedIndex.dropNontrivialIndexedStates(inputId);
            fileStatusBuilder.shouldIndex = true;
            shouldCheckContentIndexes = false;
          }
          else {
            shouldCheckContentIndexes = true;
          }
        }
        else {
          shouldCheckContentIndexes = false;
        }
        boolean fileTypeIndexAlreadyUpToData = fileTypeIndexState != null && !fileTypeIndexState.updateRequired();
        Set<ID<?, ?>> appliedIndexes = myFileBasedIndex.getAppliedIndexes(inputId);
        List<ID<?, ?>> requiredIndexes = myFileBasedIndex.getRequiredIndexes(indexedFile);

        // TODO - this non-cancelable section is just a precaution, it should be removed once we are sure
        //        the process is cancelled only when expected
        Cancellation.computeInNonCancelableSection(() -> {
          for (ID<?, ?> indexId : requiredIndexes) {
            appliedIndexes.remove(indexId);

            boolean needsFileContentLoading = myFileBasedIndex.needsFileContentLoading(indexId);
            // this is the same: (shouldCheckContentIndexes && needsFileContentLoading) || !needsFileContentLoading
            boolean shouldCheckAgainstSingleIndex = !needsFileContentLoading || shouldCheckContentIndexes;

            // if FileTypeIndex already checked, no need to check it twice
            if (shouldCheckAgainstSingleIndex && !(FileTypeIndex.NAME.equals(indexId) && fileTypeIndexAlreadyUpToData)) {
              long contentlessStartTime = needsFileContentLoading ? -1 : System.nanoTime(); // measure contentless indexes only
              try {
                applyOrScheduleRequiredIndex(indexId, fileStatusBuilder, indexedFile, inputId, indexingStamp);
              }
              finally {
                if (contentlessStartTime >= 0) {
                  fileStatusBuilder.timeUpdatingContentLessIndexes += (System.nanoTime() - contentlessStartTime);
                }
              }
            }
          }

          // remove unneeded data from indexes
          for (ID<?, ?> indexId : appliedIndexes) {
            removeIndexedValue(indexedFile, inputId, indexId, fileStatusBuilder);
          }

          if (!fileStatusBuilder.hasAppliersOrRemovers()) {
            finishGettingStatus(file, indexedFile, inputId, fileStatusBuilder, indexingStamp);
            finalization.set(EmptyRunnable.getInstance());
          }
          else {
            finalization.set(() -> {
              long applyingStart = System.nanoTime();
              try {
                fileStatusBuilder.removers.forEach(SingleIndexValueRemover::remove);
                fileStatusBuilder.appliers.forEach(SingleIndexValueApplier::apply);
              }
              finally {
                fileStatusBuilder.timeUpdatingContentLessIndexes += (System.nanoTime() - applyingStart);
              }
              finishGettingStatus(file, indexedFile, inputId, fileStatusBuilder, indexingStamp);
            });
          }
          return null;
        });
      });

      finalization.get().run();

      fileStatusBuilder.explain(indexedFile, explanationLogger);
      return fileStatusBuilder.build();
    });
  }

  private void applyOrScheduleRequiredIndex(ID<?, ?> indexId,
                                            UnindexedFileStatusBuilder fileStatusBuilder,
                                            IndexedFileImpl indexedFile,
                                            int inputId,
                                            @NotNull FileIndexingStamp indexingStamp) {
    if (FileBasedIndexScanUtil.isManuallyManaged(indexId)) return;
    if (!RebuildStatus.isOk(indexId)) {
      fileStatusBuilder.mayMarkFileIndexed = false;
      return;
    }

    try {
      FileIndexingState fileIndexingState = myFileBasedIndex.getIndexingState(indexedFile, indexId, indexingStamp);
      if (fileIndexingState == FileIndexingState.UP_TO_DATE && myShouldProcessUpToDateFiles) {
        fileIndexingState = processUpToDateFileByInfrastructureExtensions(indexedFile, inputId, indexId, fileStatusBuilder, indexingStamp);
      }
      if (fileIndexingState.updateRequired()) {
        if (FileBasedIndexEx.doTraceStubUpdates(indexId)) {
          FileBasedIndexImpl.LOG.info(
            "Scheduling indexing of " + indexedFile.getFileName() + " by request of index; " + indexId +
            (fileStatusBuilder.indexInfrastructureExtensionInvalidated ? " because extension invalidated;" : "") +
            ("indexing state = " + fileIndexingState));
        }

        fileStatusBuilder.addUnindexedState(fileIndexingState, indexId);

        if (!tryIndexWithoutContent(indexedFile, inputId, indexId, fileStatusBuilder)) {
          // NOTE! Do not break the loop here. We must process ALL IDs and pass them to the FileIndexingStatusProcessor
          // so that it can invalidate all "indexing states" (by means of clearing IndexingStamp)
          // for all indexes that became invalid. See IDEA-252846 for more details.
          fileStatusBuilder.shouldIndex = true;
        }
      }
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof IOException || cause instanceof StorageException) {
        LOG.info(e);
        myFileBasedIndex.requestRebuild(indexId, cause);
      }
      else {
        throw e;
      }
    }
  }

  private FileIndexingState processUpToDateFileByInfrastructureExtensions(IndexedFileImpl indexedFile,
                                                                          int inputId,
                                                                          ID<?, ?> indexId,
                                                                          UnindexedFileStatusBuilder fileStatusBuilder,
                                                                          @NotNull FileIndexingStamp indexingStamp) {
    // quick path: shared indexes do not have data for contentless indexes
    if (!myFileBasedIndex.needsFileContentLoading(indexId)) return FileIndexingState.UP_TO_DATE;

    long nowTime = System.nanoTime();
    try {
      FileIndexingState ret = FileIndexingState.UP_TO_DATE;
      for (FileBasedIndexInfrastructureExtension.FileIndexingStatusProcessor p : myStateProcessors) {
        if (!p.processUpToDateFile(indexedFile, inputId, indexId)) {
          fileStatusBuilder.indexInfrastructureExtensionInvalidated = true;
        }
      }
      if (fileStatusBuilder.indexInfrastructureExtensionInvalidated) {
        ret = myFileBasedIndex.getIndexingState(indexedFile, indexId, indexingStamp);
      }
      return ret;
    }
    finally {
      fileStatusBuilder.timeProcessingUpToDateFiles += (System.nanoTime() - nowTime);
    }
  }

  private void removeIndexedValue(IndexedFileImpl indexedFile,
                                     int inputId,
                                     ID<?, ?> indexId,
                                     UnindexedFileStatusBuilder fileStatusBuilder) {

    SingleIndexValueRemover remover =
      myFileBasedIndex.createSingleIndexRemover(indexId, indexedFile.getFile(), new IndexedFileWrapper(indexedFile), inputId,
                                                fileStatusBuilder.applicationMode);
    if (remover != null) {
      boolean removed = fileStatusBuilder.addOrRunRemover(remover);
      if (!removed) {
        LOG.error("Failed to remove value from index " + indexId + " for file " + indexedFile.getFile() + ", " +
                  "applicationMode=" + fileStatusBuilder.applicationMode);
      }
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
        myFileBasedIndex.createSingleIndexValueApplier(indexId, indexedFile.getFile(), inputId, new IndexedFileWrapper(indexedFile));
      if (applier != null) {
        boolean updated = fileStatusBuilder.addOrRunApplier(applier);
        if (!updated) {
          LOG.error("Failed to apply contentless indexer " + indexId + " to file " + indexedFile.getFile() + ", " +
                    "applicationMode =" + fileStatusBuilder.applicationMode);
        }
        return updated;
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
                                   @NotNull FileIndexingStamp indexingStamp) {
    if (myForceReindexingTrigger != null && myForceReindexingTrigger.test(indexedFile, indexingStamp)) {
      myFileBasedIndex.dropNontrivialIndexedStates(inputId);
      fileStatusBuilder.shouldIndex = true;
    }

    IndexingStamp.flushCache(inputId);
    if (!fileStatusBuilder.shouldIndex && fileStatusBuilder.mayMarkFileIndexed) {
      IndexingFlag.setFileIndexed(file, indexingStamp);
    }
  }

  private boolean tryIndexWithoutContentViaInfrastructureExtension(IndexedFile fileContent, int inputId, ID<?, ?> indexId) {
    for (FileBasedIndexInfrastructureExtension.FileIndexingStatusProcessor processor : myStateProcessors) {
      if (processor.tryIndexFileWithoutContent(fileContent, inputId, indexId)) {
        myFileBasedIndex.getIndex(indexId).setIndexedStateForFile(inputId, fileContent, true);
        if (FileBasedIndexEx.doTraceStubUpdates(indexId)) {
          LOG.info("File " + fileContent.getFileName() + " indexed using extension for " + indexId + " without content");
        }
        return true;
      }
    }
    return false;
  }
}