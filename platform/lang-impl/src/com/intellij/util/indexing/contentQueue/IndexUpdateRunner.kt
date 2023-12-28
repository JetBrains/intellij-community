// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.contentQueue;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.WrappedProgressIndicator;
import com.intellij.openapi.progress.impl.ProgressSuspender;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem;
import com.intellij.openapi.vfs.newvfs.impl.CachedFileType;
import com.intellij.util.PathUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.*;
import com.intellij.util.indexing.dependencies.FileIndexingStamp;
import com.intellij.util.indexing.dependencies.IndexingRequestToken;
import com.intellij.util.indexing.diagnostic.IndexingFileSetStatistics;
import com.intellij.util.indexing.diagnostic.ProjectDumbIndexingHistoryImpl;
import com.intellij.util.indexing.roots.IndexableFilesDeduplicateFilter;
import com.intellij.util.progress.SubTaskProgressIndicator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static com.intellij.util.indexing.contentQueue.IndexUpdateWriter.TOTAL_WRITERS_NUMBER;
import static com.intellij.util.indexing.contentQueue.IndexUpdateWriter.WRITE_INDEXES_ON_SEPARATE_THREAD;

@ApiStatus.Internal
public final class IndexUpdateRunner {
  private static final Logger LOG = Logger.getInstance(IndexUpdateRunner.class);

  private static final CopyOnWriteArrayList<IndexingJob> ourIndexingJobs = new CopyOnWriteArrayList<>();

  private final FileBasedIndexImpl myFileBasedIndex;
  
  private final @NotNull IndexingRequestToken indexingRequest;

  private final ExecutorService myIndexingExecutor;

  private final int myNumberOfIndexingThreads;

  private final AtomicInteger myIndexingAttemptCount = new AtomicInteger();
  private final AtomicInteger myIndexingSuccessfulCount = new AtomicInteger();


  /**
   * Number of indexing threads. We are reserving writing threads number here.
   */
  private static final int INDEXING_THREADS_NUMBER =
    Math.max(UnindexedFilesUpdater.getMaxNumberOfIndexingThreads() - TOTAL_WRITERS_NUMBER, 1);

  /**
   * Soft cap of memory we are using for loading files content during indexing process. Single file may be bigger, but until memory is freed
   * indexing threads are sleeping.
   *
   * @see #signalThatFileIsUnloaded(long)
   */
  private static final long SOFT_MAX_TOTAL_BYTES_LOADED_INTO_MEMORY = INDEXING_THREADS_NUMBER * 4L * FileUtilRt.MEGABYTE;

  /**
   * Indexing workers
   */
  private static final ExecutorService GLOBAL_INDEXING_EXECUTOR =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("Indexing", INDEXING_THREADS_NUMBER);

  /**
   * Memory optimization to prevent OutOfMemory on loading file contents.
   * <p>
   * "Soft" total limit of bytes loaded into memory in the whole application is {@link #SOFT_MAX_TOTAL_BYTES_LOADED_INTO_MEMORY}.
   * It is "soft" because one (and only one) "indexable" file can exceed this limit.
   * <p>
   * "Indexable" file is any file for which {@link FileBasedIndexImpl#isTooLarge(VirtualFile)} returns {@code false}.
   * Note that this method may return {@code false} even for relatively big files with size greater than {@link #SOFT_MAX_TOTAL_BYTES_LOADED_INTO_MEMORY}.
   * This is because for some files (or file types) the size limit is ignored.
   * <p>
   * So in its maximum we will load {@code SOFT_MAX_TOTAL_BYTES_LOADED_INTO_MEMORY + <size of not "too large" file>}, which seems acceptable,
   * because we have to index this "not too large" file anyway (even if its size is 4 Gb), and {@code SOFT_MAX_TOTAL_BYTES_LOADED_INTO_MEMORY}
   * additional bytes are insignificant.
   */
  private static long ourTotalBytesLoadedIntoMemory = 0;
  private static final Lock ourLoadedBytesLimitLock = new ReentrantLock();
  private static final Condition ourLoadedBytesAreReleasedCondition = ourLoadedBytesLimitLock.newCondition();

  public IndexUpdateRunner(@NotNull FileBasedIndexImpl fileBasedIndex,
                           @NotNull IndexingRequestToken indexingRequest, int numberOfIndexingThreads) {
    myFileBasedIndex = fileBasedIndex;
    this.indexingRequest = indexingRequest;
    myIndexingExecutor = GLOBAL_INDEXING_EXECUTOR;
    if (numberOfIndexingThreads > INDEXING_THREADS_NUMBER) {
      LOG.debug("Got request to index using " + numberOfIndexingThreads + " when pool has only " + INDEXING_THREADS_NUMBER +
               " falling back to max available");
      numberOfIndexingThreads = INDEXING_THREADS_NUMBER;
    }
    LOG.info("Using " + numberOfIndexingThreads + " indexing  and " + TOTAL_WRITERS_NUMBER + " writing threads for indexing");
    myNumberOfIndexingThreads = numberOfIndexingThreads;
  }

  /**
   * This exception contains indexing statistics accumulated by the time of a thrown exception.
   */
  public static final class IndexingInterruptedException extends Exception {
    public IndexingInterruptedException(@NotNull Throwable cause) {
      super(cause);
    }
  }

  public static final class FileSet {
    public final String debugName;
    public final @Nullable @NlsContexts.ProgressText String progressText;
    public final Collection<VirtualFile> files;
    public final IndexingFileSetStatistics statistics;

    public FileSet(@NotNull Project project, @NotNull String debugName, @NotNull Collection<VirtualFile> files) {
      this(project, debugName, files, null);
    }

    public FileSet(@NotNull Project project, @NotNull String debugName, @NotNull Collection<VirtualFile> files,
                   @Nullable @NlsContexts.ProgressText String progressText) {
      this.debugName = debugName;
      this.files = files;
      this.progressText = progressText;
      statistics = new IndexingFileSetStatistics(project, debugName);
    }
  }

  public void indexFiles(@NotNull Project project,
                         @NotNull List<FileSet> fileSets,
                         @NotNull ProgressIndicator indicator,
                         @NotNull ProjectDumbIndexingHistoryImpl projectDumbIndexingHistory) throws IndexingInterruptedException {
    long startTime = System.nanoTime();
    try {
      doIndexFiles(project, fileSets, indicator);
    }
    catch (RuntimeException e) {
      throw new IndexingInterruptedException(e);
    }
    finally {
      long visibleProcessingTime = System.nanoTime() - startTime;
      long totalProcessingTimeInAllThreads = fileSets.stream().mapToLong(b -> b.statistics.getProcessingTimeInAllThreads()).sum();
      projectDumbIndexingHistory.setVisibleTimeToAllThreadsTimeRatio(totalProcessingTimeInAllThreads == 0
                                                                     ? 0
                                                                     : ((double)visibleProcessingTime) / totalProcessingTimeInAllThreads);

      IndexUpdateWriter.waitWritingThreadsToFinish();
    }
  }

  private void doIndexFiles(@NotNull Project project, @NotNull List<FileSet> fileSets, @NotNull ProgressIndicator indicator) {
    if (ContainerUtil.and(fileSets, b -> b.files.isEmpty())) {
      return;
    }
    indicator.checkCanceled();
    indicator.setIndeterminate(false);

    CachedFileContentLoader contentLoader = new CurrentProjectHintedCachedFileContentLoader(project);
    ProgressIndicator originalIndicator = unwrapAll(indicator);
    ProgressSuspender originalSuspender = ProgressSuspender.getSuspender(originalIndicator);
    IndexingJob indexingJob = new IndexingJob(project, indicator, contentLoader, fileSets, originalIndicator, originalSuspender);
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      // If the current thread has acquired the write lock, we can't grant it to worker threads, so we must do the work in the current thread.
      while (!indexingJob.areAllFilesProcessed()) {
        indexOneFileOfJob(indexingJob);
      }
    }
    else {
      ourIndexingJobs.add(indexingJob);
      try {
        AtomicInteger numberOfRunningWorkers = new AtomicInteger();
        Runnable worker = () -> {
          try {
            indexJobsFairly();
          }
          finally {
            numberOfRunningWorkers.decrementAndGet();
          }
        };
        for (int i = 0; i < myNumberOfIndexingThreads; i++) {
          myIndexingExecutor.execute(worker);
          numberOfRunningWorkers.incrementAndGet();
        }
        while (!project.isDisposed() && !indexingJob.areAllFilesProcessed() && indexingJob.myError.get() == null) {
          // Internally checks for suspension of the indexing and blocks the current thread if necessary.
          indicator.checkCanceled();
          // Add workers if the previous have stopped for whatever reason.
          int toAddWorkersNumber = myNumberOfIndexingThreads - numberOfRunningWorkers.get();
          for (int i = 0; i < toAddWorkersNumber; i++) {
            myIndexingExecutor.execute(worker);
            numberOfRunningWorkers.incrementAndGet();
          }
          try {
            if (indexingJob.myAllFilesAreProcessedLatch.await(100, TimeUnit.MILLISECONDS)) {
              break;
            }
          }
          catch (InterruptedException e) {
            throw new ProcessCanceledException(e);
          }
        }
        Throwable error = indexingJob.myError.get();
        if (error instanceof ProcessCanceledException) {
          // The original error has happened in a different thread. Make stacktrace easier to understand by wrapping PCE into PCE
          ProcessCanceledException pce = new ProcessCanceledException();
          pce.addSuppressed(error);
          throw pce;
        }
        if (error != null) {
          throw new RuntimeException("Indexing of " + project.getName() + " has failed", error);
        }
      }
      finally {
        ourIndexingJobs.remove(indexingJob);
      }
    }
  }

  // Index jobs one by one while there are some. Jobs may belong to different projects, and we index them fairly.
  // Drops finished, canceled and failed jobs from {@code ourIndexingJobs}. Does not throw exceptions.
  private void indexJobsFairly() {
    while (!ourIndexingJobs.isEmpty()) {
      boolean allJobsAreSuspended = true;
      for (IndexingJob job : ourIndexingJobs) {
        ProgressIndicator jobIndicator = job.myIndicator;
        if (job.myProject.isDisposed()
            || job.myNoMoreFilesInQueue.get()
            || jobIndicator.isCanceled()
            || job.myError.get() != null) {
          ourIndexingJobs.remove(job);
          allJobsAreSuspended = false;
          continue;
        }
        ProgressSuspender suspender = job.myOriginalProgressSuspender;
        if (suspender != null && suspender.isSuspended()) {
          continue;
        }
        allJobsAreSuspended = false;
        try {
          Runnable work = () -> indexOneFileOfJob(job);
          if (suspender != null) {
            // Here it is important to use the original progress indicator which is directly associated with the ProgressSuspender.
            suspender.executeNonSuspendableSection(job.myOriginalProgressIndicator, work);
          } else {
            work.run();
          }
        }
        catch (Throwable e) {
          job.myError.compareAndSet(null, e);
          ourIndexingJobs.remove(job);
        }

        if(WRITE_INDEXES_ON_SEPARATE_THREAD){
          IndexUpdateWriter.sleepIfWriterQueueLarge(myNumberOfIndexingThreads);
        }
      }
      if (allJobsAreSuspended) {
        // To avoid busy-looping.
        break;
      }
    }
  }

  private void indexOneFileOfJob(@NotNull IndexingJob indexingJob) throws ProcessCanceledException {
    long startTime = System.nanoTime();
    long contentLoadingTime;
    ContentLoadingResult loadingResult;

    FileIndexingJob fileIndexingJob = indexingJob.myQueueOfFiles.poll();
    if (fileIndexingJob == null) {
      indexingJob.myNoMoreFilesInQueue.set(true);
      return;
    }

    VirtualFile file = fileIndexingJob.file;
    // snapshot at the beginning: if file changes while being processed, we can detect this on the following scanning
    FileIndexingStamp indexingStamp = indexingRequest.getFileIndexingStamp(file);
    try {
      // Propagate ProcessCanceledException and unchecked exceptions. The latter fails the whole indexing (see IndexingJob.myError).
      loadingResult = loadContent(indexingJob.myIndicator, file, indexingJob.myContentLoader);
    }
    catch (ProcessCanceledException e) {
      indexingJob.myQueueOfFiles.add(fileIndexingJob);
      throw e;
    }
    catch (TooLargeContentException e) {
      indexingJob.oneMoreFileProcessed();
      IndexingFileSetStatistics statistics = indexingJob.getStatistics(fileIndexingJob);
      synchronized (statistics) {
        statistics.addTooLargeForIndexingFile(e.getFile());
      }
      FileBasedIndexImpl.LOG.info("File: " + e.getFile().getUrl() + " is too large for indexing");
      return;
    }
    catch (FailedToLoadContentException e) {
      indexingJob.oneMoreFileProcessed();
      logFailedToLoadContentException(e);
      return;
    }
    finally {
      contentLoadingTime = System.nanoTime() - startTime;
    }

    CachedFileContent fileContent = loadingResult.cachedFileContent;
    long length = loadingResult.fileLength;

    if (file.isDirectory()) {
      LOG.info("Directory was passed for indexing unexpectedly: " + file.getPath());
    }
    
    try {
      indexingJob.setLocationBeingIndexed(fileIndexingJob);
      @NotNull Supplier<@NotNull Boolean> fileTypeChangeChecker = CachedFileType.getFileTypeChangeChecker();
      FileType type = FileTypeRegistry.getInstance().getFileTypeByFile(file, fileContent.getBytes());
      FileIndexesValuesApplier applier = ReadAction
        .nonBlocking(() -> {
          myIndexingAttemptCount.incrementAndGet();
          FileType fileType = fileTypeChangeChecker.get() ? type : null;
          return myFileBasedIndex.indexFileContent(indexingJob.myProject, fileContent, fileType, indexingStamp);
        })
        .expireWith(indexingJob.myProject)
        .wrapProgress(indexingJob.myIndicator)
        .executeSynchronously();
      myIndexingSuccessfulCount.incrementAndGet();
      if (LOG.isTraceEnabled() && myIndexingSuccessfulCount.longValue() % 10_000 == 0) {
        LOG.trace("File indexing attempts = " + myIndexingAttemptCount.longValue() + ", indexed file count = " + myIndexingSuccessfulCount.longValue());
      }

      writeIndexesForFile(indexingJob, fileIndexingJob, applier, startTime, length, contentLoadingTime);
    }
    catch (ProcessCanceledException e) {
      // Push back the file.
      indexingJob.myQueueOfFiles.add(fileIndexingJob);
      releaseFile(file, length);
      throw e;
    }
    catch (Throwable e) {
      indexingJob.oneMoreFileProcessed();
      releaseFile(file, length);
      FileBasedIndexImpl.LOG.error("Error while indexing " + file.getPresentableUrl() + "\n" +
                                   "To reindex this file IDEA has to be restarted", e);
    }
  }

  private static void writeIndexesForFile(@NotNull IndexingJob indexingJob,
                                   @NotNull FileIndexingJob fileIndexingJob,
                                   @NotNull FileIndexesValuesApplier applier,
                                   long startTime,
                                   long length,
                                   long contentLoadingTime) {
    signalThatFileIsUnloaded(length);
    long preparingTime = System.nanoTime() - startTime;
    applier.apply(fileIndexingJob.file, () -> {
      IndexingFileSetStatistics statistics = indexingJob.getStatistics(fileIndexingJob);
      synchronized (statistics) {
        var applicationTime = applier.getSeparateApplicationTimeNanos();
        statistics.addFileStatistics(fileIndexingJob.file,
                                     applier.stats,
                                     preparingTime + applicationTime,
                                     contentLoadingTime,
                                     length,
                                     applicationTime
        );
      }
      indexingJob.oneMoreFileProcessed();
      doReleaseFile(fileIndexingJob.file);
    }, false);
  }

  private static void releaseFile(VirtualFile file, long length) {
    signalThatFileIsUnloaded(length);
    doReleaseFile(file);
  }

  private static void doReleaseFile(VirtualFile file) {
    IndexingStamp.flushCache(FileBasedIndex.getFileId(file));
    IndexingFlag.unlockFile(file);
  }

  private @NotNull ContentLoadingResult loadContent(@NotNull ProgressIndicator indicator,
                                                    @NotNull VirtualFile file,
                                                    @NotNull CachedFileContentLoader loader)
    throws TooLargeContentException, FailedToLoadContentException {

    if (myFileBasedIndex.isTooLarge(file)) {
      throw new TooLargeContentException(file);
    }

    long fileLength;
    try {
      fileLength = file.getLength();
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      throw new FailedToLoadContentException(file, e);
    }

    // Reserve bytes for the file.
    waitForFreeMemoryToLoadFileContent(indicator, fileLength);

    try {
      CachedFileContent fileContent = loader.loadContent(file);
      return new ContentLoadingResult(fileContent, fileLength);
    }
    catch (Throwable e) {
      signalThatFileIsUnloaded(fileLength);
      throw e;
    }
  }

  private record ContentLoadingResult(@NotNull CachedFileContent cachedFileContent, long fileLength) {
  }

  private static void waitForFreeMemoryToLoadFileContent(@NotNull ProgressIndicator indicator,
                                                         long fileLength) throws ProcessCanceledException {
    ourLoadedBytesLimitLock.lock();
    try {
      while (ourTotalBytesLoadedIntoMemory >= SOFT_MAX_TOTAL_BYTES_LOADED_INTO_MEMORY) {
        indicator.checkCanceled();
        try {
          ourLoadedBytesAreReleasedCondition.await(100, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
          throw new ProcessCanceledException(e);
        }
      }
      ourTotalBytesLoadedIntoMemory += fileLength;
    }
    finally {
      ourLoadedBytesLimitLock.unlock();
    }
  }

  /**
   * @see #SOFT_MAX_TOTAL_BYTES_LOADED_INTO_MEMORY
   */
  private static void signalThatFileIsUnloaded(long fileLength) {
    ourLoadedBytesLimitLock.lock();
    try {
      LOG.assertTrue(ourTotalBytesLoadedIntoMemory >= fileLength);
      ourTotalBytesLoadedIntoMemory -= fileLength;
      if (ourTotalBytesLoadedIntoMemory < SOFT_MAX_TOTAL_BYTES_LOADED_INTO_MEMORY) {
        ourLoadedBytesAreReleasedCondition.signalAll();
      }
    }
    finally {
      ourLoadedBytesLimitLock.unlock();
    }
  }

  private static void logFailedToLoadContentException(@NotNull FailedToLoadContentException e) {
    Throwable cause = e.getCause();
    VirtualFile file = e.getFile();
    String fileUrl = "File: " + file.getUrl();
    if (cause instanceof FileNotFoundException || cause instanceof NoSuchFileException) {
      // It is possible to not observe file system change until refresh finish, we handle missed file properly anyway.
      FileBasedIndexImpl.LOG.debug(fileUrl, e);
    }
    else if (cause instanceof IndexOutOfBoundsException ||
             cause instanceof InvalidVirtualFileAccessException ||
             cause instanceof IOException) {
      FileBasedIndexImpl.LOG.info(fileUrl, e);
    }
    else {
      FileBasedIndexImpl.LOG.error(fileUrl, e);
    }
  }

  @NotNull
  public static @NlsSafe String getPresentableLocationBeingIndexed(@NotNull Project project, @NotNull VirtualFile file) {
    VirtualFile actualFile = file;
    if (actualFile.getFileSystem() instanceof ArchiveFileSystem) {
      actualFile = VfsUtil.getLocalFile(actualFile);
    }
    String path = getProjectRelativeOrAbsolutePath(project, actualFile);
    path = "/".equals(path) ? actualFile.getName() : path;
    return FileUtil.toSystemDependentName(path);
  }

  @NotNull
  private static String getProjectRelativeOrAbsolutePath(@NotNull Project project, @NotNull VirtualFile file) {
    String projectBase = project.getBasePath();
    if (StringUtil.isNotEmpty(projectBase)) {
      String filePath = file.getPath();
      if (FileUtil.isAncestor(projectBase, filePath, true)) {
        String projectDirName = PathUtil.getFileName(projectBase);
        String relativePath = FileUtil.getRelativePath(projectBase, filePath, '/');
        if (StringUtil.isNotEmpty(projectDirName) && StringUtil.isNotEmpty(relativePath)) {
          return projectDirName + "/" + relativePath;
        }
      }
    }
    return file.getPath();
  }

  private static @NotNull ProgressIndicator unwrapAll(@NotNull ProgressIndicator indicator) {
    // Can't use "ProgressWrapper.unwrapAll" here because it unwraps "ProgressWrapper"s only (not any "WrappedProgressIndicator")
    while (indicator instanceof WrappedProgressIndicator) {
      indicator = ((WrappedProgressIndicator)indicator).getOriginalProgressIndicator();
    }
    return indicator;
  }

  private record FileIndexingJob(VirtualFile file, FileSet fileSet) {
  }

  private static final class IndexingJob {
    final Project myProject;
    final CachedFileContentLoader myContentLoader;
    final ArrayBlockingQueue<FileIndexingJob> myQueueOfFiles; // the size for Community sources is about 615K entries
    final ProgressIndicator myIndicator;
    final int myTotalFiles;
    final AtomicBoolean myNoMoreFilesInQueue = new AtomicBoolean();
    final CountDownLatch myAllFilesAreProcessedLatch;
    final ProgressIndicator myOriginalProgressIndicator;
    @Nullable final ProgressSuspender myOriginalProgressSuspender;
    final AtomicReference<Throwable> myError = new AtomicReference<>();

    IndexingJob(@NotNull Project project,
                @NotNull ProgressIndicator indicator,
                @NotNull CachedFileContentLoader contentLoader,
                @NotNull List<FileSet> fileSets,
                @NotNull ProgressIndicator originalProgressIndicator,
                @Nullable ProgressSuspender originalProgressSuspender) {
      myProject = project;
      myIndicator = indicator;
      int maxFilesCount = fileSets.stream().mapToInt(fileSet -> fileSet.files.size()).sum();
      myQueueOfFiles = new ArrayBlockingQueue<>(maxFilesCount);
      // UnindexedFilesIndexer may produce duplicates during merging.
      // E.g., Indexer([origin:someFiles]) + Indexer[anotherOrigin:someFiles] => Indexer([origin:someFiles, anotherOrigin:someFiles])
      // Don't touch UnindexedFilesIndexer.tryMergeWith now, because eventually we want UnindexedFilesIndexer to process the queue itself
      // instead of processing and merging queue snapshots
      IndexableFilesDeduplicateFilter deduplicateFilter = IndexableFilesDeduplicateFilter.create();
      for (FileSet fileSet : fileSets) {
        for (VirtualFile file : fileSet.files) {
          if (deduplicateFilter.accept(file)) {
            myQueueOfFiles.add(new FileIndexingJob(file, fileSet));
          }
        }
      }
      // todo: maybe we want to do something with statistics: deduplicateFilter.getNumberOfSkippedFiles();
      myTotalFiles = myQueueOfFiles.size();
      myContentLoader = contentLoader;
      myAllFilesAreProcessedLatch = new CountDownLatch(myTotalFiles);
      myOriginalProgressIndicator = originalProgressIndicator;
      myOriginalProgressSuspender = originalProgressSuspender;
    }

    public @NotNull IndexingFileSetStatistics getStatistics(@NotNull FileIndexingJob fileIndexingJob) {
      return fileIndexingJob.fileSet.statistics;
    }

    public void oneMoreFileProcessed() {
      myAllFilesAreProcessedLatch.countDown();
      double newFraction = 1.0 - myAllFilesAreProcessedLatch.getCount() / (double) myTotalFiles;
      try {
        myIndicator.setFraction(newFraction);
      }
      catch (Exception ignored) {
        //Unexpected here. A misbehaved progress indicator must not break our code flow.
      }
    }

    boolean areAllFilesProcessed() {
      return myAllFilesAreProcessedLatch.getCount() == 0;
    }

    public void setLocationBeingIndexed(@NotNull FileIndexingJob fileIndexingJob) {
      String presentableLocation = getPresentableLocationBeingIndexed(myProject, fileIndexingJob.file);
      if (myIndicator instanceof SubTaskProgressIndicator) {
        myIndicator.setText(presentableLocation);
      }
      else {
        FileSet fileSet = fileIndexingJob.fileSet;
        if (fileSet.progressText != null && !fileSet.progressText.equals(myIndicator.getText())) {
          myIndicator.setText(fileSet.progressText);
        }
        myIndicator.setText2(presentableLocation);
      }
    }
  }
}