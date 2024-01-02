// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.contentQueue

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.impl.ProgressSuspender
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import com.intellij.openapi.vfs.newvfs.impl.CachedFileType
import com.intellij.util.PathUtil
import com.intellij.util.indexing.*
import com.intellij.util.indexing.IndexingFlag.unlockFile
import com.intellij.util.indexing.dependencies.IndexingRequestToken
import com.intellij.util.indexing.diagnostic.IndexingFileSetStatistics
import com.intellij.util.indexing.diagnostic.ProjectDumbIndexingHistoryImpl
import com.intellij.util.indexing.roots.IndexableFilesDeduplicateFilter
import com.intellij.util.progress.SubTaskProgressIndicator
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.annotations.ApiStatus
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

@ApiStatus.Internal
class IndexUpdateRunner(private val myFileBasedIndex: FileBasedIndexImpl,
                        private val indexingRequest: IndexingRequestToken) {
  private val myIndexingAttemptCount = AtomicInteger()
  private val myIndexingSuccessfulCount = AtomicInteger()

  init {
    LOG.info("Using $INDEXING_THREADS_NUMBER indexing and ${IndexUpdateWriter.TOTAL_WRITERS_NUMBER} writing threads for indexing")
  }

  /**
   * This exception contains indexing statistics accumulated by the time of a thrown exception.
   */
  class IndexingInterruptedException(cause: Throwable) : Exception(cause)

  class FileSet @JvmOverloads constructor(project: Project, val debugName: String, val files: Collection<VirtualFile>,
                                          val progressText: @NlsContexts.ProgressText String? = null) {
    val statistics: IndexingFileSetStatistics = IndexingFileSetStatistics(project, debugName)
  }

  @Throws(IndexingInterruptedException::class)
  fun indexFiles(project: Project,
                 fileSets: List<FileSet>,
                 projectDumbIndexingHistory: ProjectDumbIndexingHistoryImpl) {
    val startTime = System.nanoTime()
    try {
      doIndexFiles(project, fileSets)
    }
    catch (e: RuntimeException) {
      throw IndexingInterruptedException(e)
    }
    finally {
      val visibleProcessingTime = System.nanoTime() - startTime
      val totalProcessingTimeInAllThreads = fileSets.sumOf { b: FileSet -> b.statistics.processingTimeInAllThreads }
      projectDumbIndexingHistory.visibleTimeToAllThreadsTimeRatio = if (totalProcessingTimeInAllThreads == 0L
      ) 0.0
      else (visibleProcessingTime.toDouble()) / totalProcessingTimeInAllThreads

      IndexUpdateWriter.waitWritingThreadsToFinish()
    }
  }

  private fun doIndexFiles(project: Project, fileSets: List<FileSet>) {
    if (fileSets.all { b: FileSet -> b.files.isEmpty() }) {
      return
    }

    val indicator = ProgressManager.getGlobalProgressIndicator()
    indicator.checkCanceled()
    indicator.isIndeterminate = false

    val contentLoader: CachedFileContentLoader = CurrentProjectHintedCachedFileContentLoader(project)
    val originalIndicator = unwrapAll(indicator)
    val originalSuspender = ProgressSuspender.getSuspender(originalIndicator)
    // we store indicator in the IndexingJob, because want to report progress. This indicator is not used for checkCancelled()
    val indexingJob = IndexingJob(project, indicator, contentLoader, fileSets, originalIndicator, originalSuspender)

    runBlockingCancellable {
      repeat(INDEXING_THREADS_NUMBER) {
        launch(Dispatchers.IO + CoroutineName("Indexing(${project.locationHash},$it)")) {
          while (!indexingJob.areAllFilesProcessed()) {
            ensureActive()
            val suspender = indexingJob.myOriginalProgressSuspender
            while (suspender?.isSuspended == true) delay(1) // TODO: get rid of legacy suspender

            GLOBAL_INDEXING_SEMAPHORE.withPermit {
              blockingContext {
                // note that outer progress indicator does not propagate through launch(). Here (after blockingContext) we have no
                // progress indicator in the context, but ProgressManager.checkCancel() should work using outer coroutine context.
                if (suspender != null) {
                  suspender.executeNonSuspendableSection(indexingJob.myOriginalProgressIndicator) {
                    indexOneFileOfJob(indexingJob)
                  }
                }
                else {
                  indexOneFileOfJob(indexingJob)
                }

                if (IndexUpdateWriter.WRITE_INDEXES_ON_SEPARATE_THREAD) {
                  // TODO: suspend, not block
                  IndexUpdateWriter.sleepIfWriterQueueLarge(INDEXING_THREADS_NUMBER)
                }
              }
            }
          }
        }
      }
    }
  }

  @Throws(ProcessCanceledException::class)
  private fun indexOneFileOfJob(indexingJob: IndexingJob) {
    val startTime = System.nanoTime()
    val contentLoadingTime: Long
    val loadingResult: ContentLoadingResult

    val fileIndexingJob = indexingJob.myQueueOfFiles.poll()
    if (fileIndexingJob == null) {
      indexingJob.myNoMoreFilesInQueue.set(true)
      return
    }

    val file = fileIndexingJob.file
    // snapshot at the beginning: if file changes while being processed, we can detect this on the following scanning
    val indexingStamp = indexingRequest.getFileIndexingStamp(file)
    try {
      // Propagate ProcessCanceledException and unchecked exceptions. The latter fails the whole indexing.
      loadingResult = loadContent(file, indexingJob.myContentLoader)
    }
    catch (e: ProcessCanceledException) {
      indexingJob.myQueueOfFiles.add(fileIndexingJob)
      throw e
    }
    catch (e: TooLargeContentException) {
      indexingJob.oneMoreFileProcessed()
      val statistics = indexingJob.getStatistics(fileIndexingJob)
      synchronized(statistics) {
        statistics.addTooLargeForIndexingFile(e.file)
      }
      FileBasedIndexImpl.LOG.info("File: " + e.file.url + " is too large for indexing")
      return
    }
    catch (e: FailedToLoadContentException) {
      indexingJob.oneMoreFileProcessed()
      logFailedToLoadContentException(e)
      return
    }
    finally {
      contentLoadingTime = System.nanoTime() - startTime
    }

    val fileContent = loadingResult.cachedFileContent
    val length = loadingResult.fileLength

    if (file.isDirectory) {
      LOG.info("Directory was passed for indexing unexpectedly: " + file.path)
    }

    try {
      indexingJob.setLocationBeingIndexed(fileIndexingJob)
      val fileTypeChangeChecker = CachedFileType.getFileTypeChangeChecker()
      val type = FileTypeRegistry.getInstance().getFileTypeByFile(file, fileContent.bytes)
      val applier = ReadAction
        .nonBlocking<FileIndexesValuesApplier> {
          myIndexingAttemptCount.incrementAndGet()
          val fileType = if (fileTypeChangeChecker.get()) type else null
          myFileBasedIndex.indexFileContent(indexingJob.myProject, fileContent, fileType, indexingStamp)
        }
        .expireWith(indexingJob.myProject)
        .executeSynchronously()
      myIndexingSuccessfulCount.incrementAndGet()
      if (LOG.isTraceEnabled && myIndexingSuccessfulCount.toLong() % 10000 == 0L) {
        LOG.trace(
          "File indexing attempts = " + myIndexingAttemptCount.toLong() + ", indexed file count = " + myIndexingSuccessfulCount.toLong())
      }

      writeIndexesForFile(indexingJob, fileIndexingJob, applier, startTime, length, contentLoadingTime)
    }
    catch (e: ProcessCanceledException) {
      // Push back the file.
      indexingJob.myQueueOfFiles.add(fileIndexingJob)
      releaseFile(file, length)
      throw e
    }
    catch (e: Throwable) {
      indexingJob.oneMoreFileProcessed()
      releaseFile(file, length)
      FileBasedIndexImpl.LOG.error("""
  Error while indexing ${file.presentableUrl}
  To reindex this file IDEA has to be restarted
  """.trimIndent(), e)
    }
  }

  @Throws(TooLargeContentException::class, FailedToLoadContentException::class)
  private fun loadContent(file: VirtualFile,
                          loader: CachedFileContentLoader): ContentLoadingResult {
    if (myFileBasedIndex.isTooLarge(file)) {
      throw TooLargeContentException(file)
    }

    val fileLength: Long
    try {
      fileLength = file.length
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      throw FailedToLoadContentException(file, e)
    }

    // Reserve bytes for the file.
    waitForFreeMemoryToLoadFileContent(fileLength)

    try {
      val fileContent = loader.loadContent(file)
      return ContentLoadingResult(fileContent, fileLength)
    }
    catch (e: Throwable) {
      signalThatFileIsUnloaded(fileLength)
      throw e
    }
  }

  @JvmRecord
  private data class ContentLoadingResult(val cachedFileContent: CachedFileContent, val fileLength: Long)

  @JvmRecord
  private data class FileIndexingJob(val file: VirtualFile, val fileSet: FileSet)

  private class IndexingJob(val myProject: Project,
                            private val indicatorForProgressReporting: ProgressIndicator, // should only be used for progress reporting
                            contentLoader: CachedFileContentLoader,
                            fileSets: List<FileSet>,
                            originalProgressIndicator: ProgressIndicator,
                            originalProgressSuspender: ProgressSuspender?) {
    val myContentLoader: CachedFileContentLoader
    val myQueueOfFiles: ArrayBlockingQueue<FileIndexingJob> // the size for Community sources is about 615K entries
    val myTotalFiles: Int
    val myNoMoreFilesInQueue: AtomicBoolean = AtomicBoolean()
    val myAllFilesAreProcessedLatch: CountDownLatch
    val myOriginalProgressIndicator: ProgressIndicator
    val myOriginalProgressSuspender: ProgressSuspender?

    init {
      val maxFilesCount = fileSets.sumOf { fileSet: FileSet -> fileSet.files.size }
      myQueueOfFiles = ArrayBlockingQueue(maxFilesCount)
      // UnindexedFilesIndexer may produce duplicates during merging.
      // E.g., Indexer([origin:someFiles]) + Indexer[anotherOrigin:someFiles] => Indexer([origin:someFiles, anotherOrigin:someFiles])
      // Don't touch UnindexedFilesIndexer.tryMergeWith now, because eventually we want UnindexedFilesIndexer to process the queue itself
      // instead of processing and merging queue snapshots
      val deduplicateFilter = IndexableFilesDeduplicateFilter.create()
      for (fileSet in fileSets) {
        for (file in fileSet.files) {
          if (deduplicateFilter.accept(file)) {
            myQueueOfFiles.add(FileIndexingJob(file, fileSet))
          }
        }
      }
      // todo: maybe we want to do something with statistics: deduplicateFilter.getNumberOfSkippedFiles();
      myTotalFiles = myQueueOfFiles.size
      myContentLoader = contentLoader
      myAllFilesAreProcessedLatch = CountDownLatch(myTotalFiles)
      myOriginalProgressIndicator = originalProgressIndicator
      myOriginalProgressSuspender = originalProgressSuspender
    }

    fun getStatistics(fileIndexingJob: FileIndexingJob): IndexingFileSetStatistics {
      return fileIndexingJob.fileSet.statistics
    }

    fun oneMoreFileProcessed() {
      myAllFilesAreProcessedLatch.countDown()
      val newFraction = 1.0 - myAllFilesAreProcessedLatch.count / myTotalFiles.toDouble()
      try {
        indicatorForProgressReporting.fraction = newFraction
      }
      catch (ignored: Exception) {
        //Unexpected here. A misbehaved progress indicator must not break our code flow.
      }
    }

    fun areAllFilesProcessed(): Boolean {
      return myAllFilesAreProcessedLatch.count == 0L
    }

    fun setLocationBeingIndexed(fileIndexingJob: FileIndexingJob) {
      val presentableLocation = getPresentableLocationBeingIndexed(myProject, fileIndexingJob.file)
      if (indicatorForProgressReporting is SubTaskProgressIndicator) {
        indicatorForProgressReporting.setText(presentableLocation)
      }
      else {
        val fileSet = fileIndexingJob.fileSet
        if (fileSet.progressText != null && fileSet.progressText != indicatorForProgressReporting.text) {
          indicatorForProgressReporting.text = fileSet.progressText
        }
        indicatorForProgressReporting.text2 = presentableLocation
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(IndexUpdateRunner::class.java)

    /**
     * Number of indexing threads. Writing threads are counted separately, because they are "mostly waiting IO" threads.
     */
    private val INDEXING_THREADS_NUMBER: Int = UnindexedFilesUpdater.getMaxNumberOfIndexingThreads()

    /**
     * Soft cap of memory we are using for loading files content during indexing process. Single file may be bigger, but until memory is freed
     * indexing threads are sleeping.
     *
     * @see .signalThatFileIsUnloaded
     */
    private val SOFT_MAX_TOTAL_BYTES_LOADED_INTO_MEMORY = INDEXING_THREADS_NUMBER * 4L * FileUtilRt.MEGABYTE

    /**
     * Indexing workers
     */
    private val GLOBAL_INDEXING_SEMAPHORE = Semaphore(INDEXING_THREADS_NUMBER)

    /**
     * Memory optimization to prevent OutOfMemory on loading file contents.
     *
     *
     * "Soft" total limit of bytes loaded into memory in the whole application is [.SOFT_MAX_TOTAL_BYTES_LOADED_INTO_MEMORY].
     * It is "soft" because one (and only one) "indexable" file can exceed this limit.
     *
     *
     * "Indexable" file is any file for which [FileBasedIndexImpl.isTooLarge] returns `false`.
     * Note that this method may return `false` even for relatively big files with size greater than [.SOFT_MAX_TOTAL_BYTES_LOADED_INTO_MEMORY].
     * This is because for some files (or file types) the size limit is ignored.
     *
     *
     * So in its maximum we will load `SOFT_MAX_TOTAL_BYTES_LOADED_INTO_MEMORY + <size of not "too large" file>`, which seems acceptable,
     * because we have to index this "not too large" file anyway (even if its size is 4 Gb), and `SOFT_MAX_TOTAL_BYTES_LOADED_INTO_MEMORY`
     * additional bytes are insignificant.
     */
    private var ourTotalBytesLoadedIntoMemory: Long = 0
    private val ourLoadedBytesLimitLock: Lock = ReentrantLock()
    private val ourLoadedBytesAreReleasedCondition: Condition = ourLoadedBytesLimitLock.newCondition()

    private fun writeIndexesForFile(indexingJob: IndexingJob,
                                    fileIndexingJob: FileIndexingJob,
                                    applier: FileIndexesValuesApplier,
                                    startTime: Long,
                                    length: Long,
                                    contentLoadingTime: Long) {
      signalThatFileIsUnloaded(length)
      val preparingTime = System.nanoTime() - startTime
      applier.apply(fileIndexingJob.file, {
        val statistics = indexingJob.getStatistics(fileIndexingJob)
        synchronized(statistics) {
          val applicationTime = applier.separateApplicationTimeNanos
          statistics.addFileStatistics(fileIndexingJob.file,
                                       applier.stats,
                                       preparingTime + applicationTime,
                                       contentLoadingTime,
                                       length,
                                       applicationTime
          )
        }
        indexingJob.oneMoreFileProcessed()
        doReleaseFile(fileIndexingJob.file)
      }, false)
    }

    private fun releaseFile(file: VirtualFile, length: Long) {
      signalThatFileIsUnloaded(length)
      doReleaseFile(file)
    }

    private fun doReleaseFile(file: VirtualFile) {
      IndexingStamp.flushCache(FileBasedIndex.getFileId(file))
      unlockFile(file)
    }

    @Throws(ProcessCanceledException::class)
    private fun waitForFreeMemoryToLoadFileContent(fileLength: Long) {
      ourLoadedBytesLimitLock.lock()
      try {
        while (ourTotalBytesLoadedIntoMemory >= SOFT_MAX_TOTAL_BYTES_LOADED_INTO_MEMORY) {
          ProgressManager.checkCanceled()
          try {
            ourLoadedBytesAreReleasedCondition.await(100, TimeUnit.MILLISECONDS)
          }
          catch (e: InterruptedException) {
            throw ProcessCanceledException(e)
          }
        }
        ourTotalBytesLoadedIntoMemory += fileLength
      }
      finally {
        ourLoadedBytesLimitLock.unlock()
      }
    }

    /**
     * @see .SOFT_MAX_TOTAL_BYTES_LOADED_INTO_MEMORY
     */
    private fun signalThatFileIsUnloaded(fileLength: Long) {
      ourLoadedBytesLimitLock.lock()
      try {
        LOG.assertTrue(ourTotalBytesLoadedIntoMemory >= fileLength)
        ourTotalBytesLoadedIntoMemory -= fileLength
        if (ourTotalBytesLoadedIntoMemory < SOFT_MAX_TOTAL_BYTES_LOADED_INTO_MEMORY) {
          ourLoadedBytesAreReleasedCondition.signalAll()
        }
      }
      finally {
        ourLoadedBytesLimitLock.unlock()
      }
    }

    private fun logFailedToLoadContentException(e: FailedToLoadContentException) {
      val cause = e.cause
      val file = e.file
      val fileUrl = "File: " + file.url
      when (cause) {
        is FileNotFoundException, is NoSuchFileException -> {
          // It is possible to not observe file system change until refresh finish, we handle missed file properly anyway.
          FileBasedIndexImpl.LOG.debug(fileUrl, e)
        }
        is IndexOutOfBoundsException, is InvalidVirtualFileAccessException, is IOException -> {
          FileBasedIndexImpl.LOG.info(fileUrl, e)
        }
        else -> {
          FileBasedIndexImpl.LOG.error(fileUrl, e)
        }
      }
    }

    fun getPresentableLocationBeingIndexed(project: Project, file: VirtualFile): @NlsSafe String {
      var actualFile = file
      if (actualFile.fileSystem is ArchiveFileSystem) {
        actualFile = VfsUtil.getLocalFile(actualFile)
      }
      var path = getProjectRelativeOrAbsolutePath(project, actualFile)
      path = if ("/" == path) actualFile.name else path
      return FileUtil.toSystemDependentName(path)
    }

    private fun getProjectRelativeOrAbsolutePath(project: Project, file: VirtualFile): String {
      val projectBase = project.basePath
      if (StringUtil.isNotEmpty(projectBase)) {
        val filePath = file.path
        if (FileUtil.isAncestor(projectBase!!, filePath, true)) {
          val projectDirName = PathUtil.getFileName(projectBase)
          val relativePath = FileUtil.getRelativePath(projectBase, filePath, '/')
          if (StringUtil.isNotEmpty(projectDirName) && StringUtil.isNotEmpty(relativePath)) {
            return "$projectDirName/$relativePath"
          }
        }
      }
      return file.path
    }

    private fun unwrapAll(indicator: ProgressIndicator): ProgressIndicator {
      // Can't use "ProgressWrapper.unwrapAll" here because it unwraps "ProgressWrapper"s only (not any "WrappedProgressIndicator")
      var unwrapped = indicator
      while (unwrapped is WrappedProgressIndicator) {
        unwrapped = unwrapped.originalProgressIndicator
      }
      return unwrapped
    }
  }
}