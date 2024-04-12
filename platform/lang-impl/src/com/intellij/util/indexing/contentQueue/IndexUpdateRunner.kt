// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.contentQueue

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.impl.ProgressSuspender
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
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
import com.intellij.util.application
import com.intellij.util.indexing.*
import com.intellij.util.indexing.IndexingFlag.unlockFile
import com.intellij.util.indexing.dependencies.FileIndexingStamp
import com.intellij.util.indexing.dependencies.IndexingRequestToken
import com.intellij.util.indexing.diagnostic.IndexingFileSetStatistics
import com.intellij.util.indexing.diagnostic.ProjectDumbIndexingHistoryImpl
import com.intellij.util.indexing.events.FileIndexingRequest
import com.intellij.util.indexing.roots.IndexableFilesDeduplicateFilter
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.annotations.ApiStatus
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max

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

  class FileSet @JvmOverloads constructor(project: Project, val debugName: String, internal val files: Collection<FileIndexingRequest>,
                                          val progressText: @NlsContexts.ProgressText String? = null) {
    val statistics: IndexingFileSetStatistics = IndexingFileSetStatistics(project, debugName)

    fun isEmpty(): Boolean = files.isEmpty()
    fun size(): Int = files.size
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

  // TODO: this method should be removed, and regular runBlockingCancellable should be used after IJPL-578 is implemented
  //  this is a copy-paste code from platform's runBlockingCancellable with the only one change: invocation from EDT is allowed
  @OptIn(IntellijInternalApi::class)
  private fun <T> runBlockingCancellableAllowingEdtInHeadlessModeWithNoEventsPumpingIfEdt(action: suspend CoroutineScope.() -> T): T {
    LOG.assertTrue(DumbServiceImpl.isSynchronousTaskExecution || !application.isDispatchThread,
                   "This method may only be invoked on EDT in headless environments")

    return prepareThreadContext { ctx ->
      if (ctx[Job] == null && !Cancellation.isInNonCancelableSection()) {
        LOG.error(IllegalStateException("There is no ProgressIndicator or Job in this thread, the current job is not cancellable."))
      }
      try {
        @Suppress("RAW_RUN_BLOCKING")
        runBlocking(ctx + readActionContext(), action)
      }
      catch (pce : ProcessCanceledException) {
        throw pce
      }
      catch (ce: CancellationException) {
        throw CeProcessCanceledException(ce)
      }
    }
  }

  private fun doIndexFiles(project: Project, fileSets: List<FileSet>) {
    if (fileSets.all { b: FileSet -> b.isEmpty() }) {
      return
    }

    val indicator = ProgressManager.getGlobalProgressIndicator()
    indicator.checkCanceled()
    indicator.isIndeterminate = false

    val contentLoader: CachedFileContentLoader = CurrentProjectHintedCachedFileContentLoader(project)
    val originalSuspender = ProgressSuspender.getSuspender(unwrapAll(indicator))
    val progressReporter = IndexingProgressReporter2(indicator)
    val indexingJob = IndexingJob(project, progressReporter, contentLoader, fileSets)

    runBlockingCancellableAllowingEdtInHeadlessModeWithNoEventsPumpingIfEdt {
      repeat(INDEXING_THREADS_NUMBER) {
        launch(Dispatchers.IO + CoroutineName("Indexing(${project.locationHash},$it)")) {
          while (!indexingJob.areAllFilesProcessed()) {
            ensureActive()
            while (originalSuspender?.isSuspended == true) delay(1) // TODO: get rid of legacy suspender

            GLOBAL_INDEXING_SEMAPHORE.withPermit {
              blockingContext {
                LOG.assertTrue(ProgressManager.getGlobalProgressIndicator() == null,
                               "There should be no global progress indicator, because it does not propagate implicitly through launch()")

                // and since there is no progress indicator, we don't need "originalSuspender.executeNonSuspendableSection"
                // (there is no way to access originalSuspender or indicator from inside indexOneFileOfJob)
                indexOneFileOfJob(indexingJob)

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

    val fileIndexingJob = indexingJob.myQueueOfFiles.poll()
    if (fileIndexingJob == null) {
      return
    }
    indexingJob.setLocationBeingIndexed(fileIndexingJob)

    val fileIndexingRequest = fileIndexingJob.fileIndexingRequest
    // snapshot at the beginning: if file changes while being processed, we can detect this on the following scanning
    val indexingStamp = indexingRequest.getFileIndexingStamp(fileIndexingRequest.file)

    if (fileIndexingRequest.file.isDirectory) {
      LOG.info("Directory was passed for indexing unexpectedly: " + fileIndexingRequest.file.path)
    }

    try {
      val (applier, contentLoadingTime, length) = if (fileIndexingJob.fileIndexingRequest.isDeleteRequest) {
        val applierOrNullIfResurrected = getApplierForFileIndexDelete(indexingStamp, fileIndexingRequest.file, indexingJob)
        if (applierOrNullIfResurrected == null) {
          getApplierForFileIndexUpdate(indexingStamp, startTime, fileIndexingRequest.file, indexingJob)
        }
        else {
          Triple(applierOrNullIfResurrected, 0L, 0L)
        }
      }
      else {
        getApplierForFileIndexUpdate(indexingStamp, startTime, fileIndexingRequest.file, indexingJob)
      }

      try {
        writeIndexesForFile(indexingJob, fileIndexingJob, applier, startTime, length, contentLoadingTime)
      }
      catch (t: Throwable) {
        releaseFile(fileIndexingRequest.file) // the file is "locked" in the applier constructor
        throw t
      }
    }
    catch (e: ProcessCanceledException) {
      // Push back the file.
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
    }
    catch (e: FailedToLoadContentException) {
      indexingJob.oneMoreFileProcessed()
      logFailedToLoadContentException(e)
    }
    catch (e: Throwable) {
      indexingJob.oneMoreFileProcessed()
      FileBasedIndexImpl.LOG.error("""
  Error while indexing ${fileIndexingRequest.file.presentableUrl}
  To reindex this file IDEA has to be restarted
  """.trimIndent(), e)
    }
  }

  private fun incIndexingSuccessfulCountAndLogIfNeeded(){
    myIndexingSuccessfulCount.incrementAndGet()
    if (LOG.isTraceEnabled && myIndexingSuccessfulCount.toLong() % 10000 == 0L) {
      LOG.trace("File indexing attempts = ${myIndexingAttemptCount.get()}, indexed file count = ${myIndexingSuccessfulCount.get()}")
    }
  }

  private fun getApplierForFileIndexDelete(indexingStamp: FileIndexingStamp,
                                           file: VirtualFile, indexingJob: IndexingJob): FileIndexesValuesApplier? {
    val applier = ReadAction
      .nonBlocking<FileIndexesValuesApplier?> {
        myFileBasedIndex.getApplierToRemoveDataFromIndexesForFile(file, indexingStamp)
      }
      .expireWith(indexingJob.myProject)
      .executeSynchronously()
    incIndexingSuccessfulCountAndLogIfNeeded()
    return applier
  }

  private fun getApplierForFileIndexUpdate(indexingStamp: FileIndexingStamp, startTime: Long,
                                           file: VirtualFile, indexingJob: IndexingJob): Triple<FileIndexesValuesApplier, Long, Long> {
    // Propagate ProcessCanceledException and unchecked exceptions. The latter fails the whole indexing.
    val loadingResult: ContentLoadingResult = loadContent(file, indexingJob.myContentLoader)
    val contentLoadingTime: Long = System.nanoTime() - startTime

    val fileContent = loadingResult.cachedFileContent
    val length = loadingResult.fileLength

    try {
      val fileTypeChangeChecker = CachedFileType.getFileTypeChangeChecker()
      val type = FileTypeRegistry.getInstance().getFileTypeByFile(file, fileContent.bytes)
      val applier = ReadAction
        .nonBlocking<FileIndexesValuesApplier> {
          myIndexingAttemptCount.incrementAndGet()
          val fileType = if (fileTypeChangeChecker.get()) type else null
          myFileBasedIndex.indexFileContent(indexingJob.myProject, fileContent, false, fileType, indexingStamp)
        }
        .expireWith(indexingJob.myProject)
        .executeSynchronously()
      incIndexingSuccessfulCountAndLogIfNeeded()
      return Triple(applier, contentLoadingTime, length)
    }
    finally {
      signalThatFileIsUnloaded(length)
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
  private data class FileIndexingJob(val fileIndexingRequest: FileIndexingRequest, val fileSet: FileSet)

  private class IndexingJob(val myProject: Project,
                            val progressReporter: IndexingProgressReporter2,
                            val myContentLoader: CachedFileContentLoader,
                            fileSets: List<FileSet>) {
    val myQueueOfFiles: ArrayBlockingQueue<FileIndexingJob> // the size for Community sources is about 615K entries

    init {
      val maxFilesCount = fileSets.sumOf { fileSet: FileSet -> fileSet.size() }
      myQueueOfFiles = ArrayBlockingQueue(maxFilesCount)
      // UnindexedFilesIndexer may produce duplicates during merging.
      // E.g., Indexer([origin:someFiles]) + Indexer[anotherOrigin:someFiles] => Indexer([origin:someFiles, anotherOrigin:someFiles])
      // Don't touch UnindexedFilesIndexer.tryMergeWith now, because eventually we want UnindexedFilesIndexer to process the queue itself
      // instead of processing and merging queue snapshots
      val deduplicateFilter = IndexableFilesDeduplicateFilter.create()
      for (fileSet in fileSets) {
        for (file in fileSet.files) {
          if (deduplicateFilter.accept(file.file)) {
            myQueueOfFiles.add(FileIndexingJob(file, fileSet))
          }
        }
      }
      // todo: maybe we want to do something with statistics: deduplicateFilter.getNumberOfSkippedFiles();
      progressReporter.setTotalFiles(myQueueOfFiles.size)
    }

    fun getStatistics(fileIndexingJob: FileIndexingJob): IndexingFileSetStatistics {
      return fileIndexingJob.fileSet.statistics
    }

    fun oneMoreFileProcessed() {
      progressReporter.oneMoreFileProcessed()
    }

    fun areAllFilesProcessed(): Boolean {
      return myQueueOfFiles.isEmpty()
    }

    fun setLocationBeingIndexed(fileIndexingJob: FileIndexingJob) {
      val presentableLocation = getPresentableLocationBeingIndexed(myProject, fileIndexingJob.fileIndexingRequest.file)
      progressReporter.setLocationBeingIndexed(fileIndexingJob.fileSet.progressText, presentableLocation)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(IndexUpdateRunner::class.java)

    /**
     * Number of indexing threads. In ideal scenario writing threads are 100% busy, so we are taking them into account.
     */
    private val INDEXING_THREADS_NUMBER: Int = max(
      UnindexedFilesUpdater.getMaxNumberOfIndexingThreads() - IndexUpdateWriter.TOTAL_WRITERS_NUMBER, 1)

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
      val preparingTime = System.nanoTime() - startTime
      applier.apply(fileIndexingJob.fileIndexingRequest.file, {
        val statistics = indexingJob.getStatistics(fileIndexingJob)
        synchronized(statistics) {
          val applicationTime = applier.separateApplicationTimeNanos
          statistics.addFileStatistics(fileIndexingJob.fileIndexingRequest.file,
                                       applier.stats,
                                       preparingTime + applicationTime,
                                       contentLoadingTime,
                                       length,
                                       applicationTime
          )
        }
        indexingJob.oneMoreFileProcessed()
        releaseFile(fileIndexingJob.fileIndexingRequest.file)
      }, false)
    }

    private fun releaseFile(file: VirtualFile) {
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