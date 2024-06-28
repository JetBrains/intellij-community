// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.contentQueue

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.InvalidVirtualFileAccessException
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import com.intellij.openapi.vfs.newvfs.impl.CachedFileType
import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.util.PathUtil
import com.intellij.util.indexing.*
import com.intellij.util.indexing.IndexingFlag.unlockFile
import com.intellij.util.indexing.PerProjectIndexingQueue.QueuedFiles
import com.intellij.util.indexing.contentQueue.dev.IndexWriter
import com.intellij.util.indexing.contentQueue.dev.TOTAL_WRITERS_NUMBER
import com.intellij.util.indexing.dependencies.FileIndexingStamp
import com.intellij.util.indexing.dependencies.IndexingRequestToken
import com.intellij.util.indexing.diagnostic.IndexingFileSetStatistics
import com.intellij.util.indexing.diagnostic.ProjectDumbIndexingHistoryImpl
import com.intellij.util.indexing.events.FileIndexingRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

@ApiStatus.Internal
class IndexUpdateRunner(
  fileBasedIndex: FileBasedIndexImpl,
  indexingRequest: IndexingRequestToken,
) {

  private val indexer: Indexer = Indexer(fileBasedIndex, indexingRequest)

  init {
    if (IndexWriter.WRITE_INDEXES_ON_SEPARATE_THREAD) {
      LOG.info("Using ${INDEXING_PARALLELIZATION} workers for indexing and ${TOTAL_WRITERS_NUMBER} for writing indexes")
    }
    else {
      LOG.info("Using ${INDEXING_PARALLELIZATION} workers for indexing, and writing indexes on the same worker")
    }
  }

  /**
   * This exception contains indexing statistics accumulated by the time of a thrown exception.
   */
  class IndexingInterruptedException(cause: Throwable) : Exception(cause)

  class FileSet(
    project: Project, val debugName: String,
    internal val filesOriginal: QueuedFiles,
    val shouldPause: () -> Boolean,
  ) {
    @TestOnly
    constructor(project: Project, debugName: String, filesOriginal: QueuedFiles) : this(project, debugName, filesOriginal, { false })

    val statistics: IndexingFileSetStatistics = IndexingFileSetStatistics(project, debugName)

    fun isEmpty(): Boolean = filesOriginal.isEmpty
    fun size(): Int = filesOriginal.size

    fun asChannel(cs: CoroutineScope): Channel<FileIndexingRequest> = filesOriginal.asChannel(cs, INDEXING_PARALLELIZATION * 2)
  }

  @Throws(IndexingInterruptedException::class)
  fun indexFiles(
    project: Project,
    fileSet: FileSet,
    projectDumbIndexingHistory: ProjectDumbIndexingHistoryImpl,
    progressReporter: IndexingProgressReporter2,
  ) {
    val startTime = System.nanoTime()
    try {
      doIndexFiles(project, fileSet, progressReporter)
    }
    catch (e: RuntimeException) {
      throw IndexingInterruptedException(e)
    }
    finally {
      val visibleProcessingTime = System.nanoTime() - startTime
      val totalProcessingTimeInAllThreads = fileSet.statistics.processingTimeInAllThreads
      projectDumbIndexingHistory.visibleTimeToAllThreadsTimeRatio = if (totalProcessingTimeInAllThreads == 0L
      ) 0.0
      else (visibleProcessingTime.toDouble()) / totalProcessingTimeInAllThreads
    }
  }

  private fun doIndexFiles(
    project: Project, fileSet: FileSet,
    progressReporter: IndexingProgressReporter2,
  ) {
    if (fileSet.isEmpty()) {
      return
    }

    val contentLoader: CachedFileContentLoader = CurrentProjectHintedCachedFileContentLoader(project)

    processFileSetInParallel(project, fileSet) { fileIndexingRequest ->
      //blockingContext {
      val presentableLocation = getPresentableLocationBeingIndexed(project, fileIndexingRequest.file)
      progressReporter.setLocationBeingIndexed(presentableLocation)
      indexOneFileHandleExceptions(fileIndexingRequest, project, project, contentLoader, fileSet.statistics)
      progressReporter.oneMoreFileProcessed()
      //}
    }
  }

  private fun processFileSetInParallel(
    project: Project,
    fileSet: FileSet,
    processRequestTask: suspend (FileIndexingRequest) -> Unit,
  ) {
    runBlockingCancellable {

      withContext(Dispatchers.IO + CoroutineName("Indexing(${project.locationHash}")) {
        //Ideally, we should launch a coroutine for each file in a fileSet, and let the coroutine scheduler do it's job
        // of distributing the load across available CPUs.
        // But the fileSet could be quite large (10-100-1000k files), so it could be quite a load for a scheduler.
        // So an optimization: we use fixed number of coroutines (approx. = # available CPUs), and a channel:

        fileSet.filesOriginal.requests.forEachConcurrent(concurrency = INDEXING_PARALLELIZATION) { fileIndexingRequest ->
          while (fileSet.shouldPause()) { // TODO: get rid of legacy suspender
            delay(1)
          }
          processRequestTask(fileIndexingRequest)
        }

        //TODO RC: assumed implicit knowledge that defaultParallelWriter is the writer responsible for the
        //         index writing down the stack. But what if it is not?
        IndexWriter.defaultParallelWriter().waitCurrentIndexingToFinish()
      }
    }
  }

  @Throws(ProcessCanceledException::class)
  private suspend fun indexOneFileHandleExceptions(
    fileIndexingRequest: FileIndexingRequest,
    project: Project,
    parentDisposableForInvokeLater: Disposable,
    contentLoader: CachedFileContentLoader,
    statistics: IndexingFileSetStatistics,
  ) {
    val startTime = System.nanoTime()

    try {
      if (fileIndexingRequest.file.isDirectory) {
        LOG.info("Directory was passed for indexing unexpectedly: " + fileIndexingRequest.file.path)
      }

      indexer.indexOneFile(fileIndexingRequest, parentDisposableForInvokeLater, startTime, project, contentLoader, statistics)
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: TooLargeContentException) {
      synchronized(statistics) {
        statistics.addTooLargeForIndexingFile(e.file)
      }
      FileBasedIndexImpl.LOG.info("File: " + e.file.url + " is too large for indexing")
    }
    catch (e: FailedToLoadContentException) {
      logFailedToLoadContentException(e)
    }
    catch (e: Throwable) {
      FileBasedIndexImpl.LOG.error("""
  Error while indexing ${fileIndexingRequest.file.presentableUrl}
  To reindex this file IDEA has to be restarted
  """.trimIndent(), e)
    }
  }

  @ApiStatus.Internal
  class Indexer(
    private val fileBasedIndex: FileBasedIndexImpl,
    private val indexingRequest: IndexingRequestToken,
  ) {

    private val indexingAttemptCount = AtomicInteger()
    private val indexingSuccessfulCount = AtomicInteger()

    suspend fun indexOneFile(
      fileIndexingRequest: FileIndexingRequest,
      parentDisposableForInvokeLater: Disposable,
      startTime: Long,
      project: Project,
      contentLoader: CachedFileContentLoader,
      statistics: IndexingFileSetStatistics,
    ) {

      // snapshot at the beginning: if file changes while being processed, we can detect this on the following scanning
      val file = fileIndexingRequest.file
      val indexingStamp = indexingRequest.getFileIndexingStamp(file)

      val (applier, contentLoadingTime, length) = if (fileIndexingRequest.isDeleteRequest) {
        val applierOrNullIfResurrected = getApplierForFileIndexDelete(indexingStamp, file, parentDisposableForInvokeLater)
        if (applierOrNullIfResurrected == null) {
          getApplierForFileIndexUpdate(indexingStamp, startTime, file, parentDisposableForInvokeLater, project, contentLoader)
        }
        else {
          Triple(applierOrNullIfResurrected, 0L, 0L)
        }
      }
      else {
        getApplierForFileIndexUpdate(indexingStamp, startTime, file, parentDisposableForInvokeLater, project, contentLoader)
      }

      try {
        writeIndexesForFile(applier, statistics, startTime, length, contentLoadingTime)
      }
      catch (t: Throwable) {
        releaseFile(file) // the file is "locked" in the applier constructor
        throw t
      }
    }

    private fun incIndexingSuccessfulCountAndLogIfNeeded() {
      indexingSuccessfulCount.incrementAndGet()
      if (LOG.isTraceEnabled && indexingSuccessfulCount.toLong() % 10000 == 0L) {
        LOG.trace("File indexing attempts = ${indexingAttemptCount.get()}, indexed file count = ${indexingSuccessfulCount.get()}")
      }
    }

    private fun getApplierForFileIndexDelete(
      indexingStamp: FileIndexingStamp,
      file: VirtualFile, parentDisposable: Disposable,
    ): FileIndexingResult? {
      val fileIndexingResult = ReadAction
        .nonBlocking<FileIndexingResult?> {
          fileBasedIndex.getApplierToRemoveDataFromIndexesForFile(file, indexingStamp)
        }
        .expireWith(parentDisposable)
        .executeSynchronously()
      incIndexingSuccessfulCountAndLogIfNeeded()
      return fileIndexingResult
    }

    private fun getApplierForFileIndexUpdate(
      indexingStamp: FileIndexingStamp, startTime: Long,
      file: VirtualFile,
      parentDisposable: Disposable,
      project: Project,
      loader: CachedFileContentLoader,
    ): Triple<FileIndexingResult, Long, Long> {
      // Propagate ProcessCanceledException and unchecked exceptions. The latter fails the whole indexing.
      val loadingResult: ContentLoadingResult = loadContent(file, loader)
      val contentLoadingTime: Long = System.nanoTime() - startTime

      val fileContent = loadingResult.cachedFileContent
      val length = loadingResult.fileLength

      try {
        val fileTypeChangeChecker = CachedFileType.getFileTypeChangeChecker()
        val type = FileTypeRegistry.getInstance().getFileTypeByFile(file, fileContent.bytes)
        val fileIndexingResult = ReadAction
          .nonBlocking<FileIndexingResult> {
            indexingAttemptCount.incrementAndGet()
            val fileType = if (fileTypeChangeChecker.get()) type else null
            fileBasedIndex.indexFileContent(project, fileContent, false, fileType, indexingStamp)
          }
          .expireWith(parentDisposable)
          .executeSynchronously()
        incIndexingSuccessfulCountAndLogIfNeeded()
        return Triple(fileIndexingResult, contentLoadingTime, length)
      }
      finally {
        signalThatFileIsUnloaded(length)
      }
    }

    @Throws(TooLargeContentException::class, FailedToLoadContentException::class)
    private fun loadContent(
      file: VirtualFile,
      loader: CachedFileContentLoader,
    ): ContentLoadingResult {
      if (fileBasedIndex.isTooLarge(file)) {
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
  }

  @JvmRecord
  private data class ContentLoadingResult(val cachedFileContent: CachedFileContent, val fileLength: Long)

  companion object {
    private val LOG = Logger.getInstance(IndexUpdateRunner::class.java)

    /** Number indexing tasks to run in parallel */
    val INDEXING_PARALLELIZATION: Int = UnindexedFilesUpdater.getMaxNumberOfIndexingThreads()

    /**
     * Soft cap of memory we are using for loading files content during indexing process. Single file may be bigger, but until memory is freed
     * indexing threads are sleeping.
     *
     * @see .signalThatFileIsUnloaded
     */
    private val SOFT_MAX_TOTAL_BYTES_LOADED_INTO_MEMORY = INDEXING_PARALLELIZATION * 4L * FileUtilRt.MEGABYTE

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

    private suspend fun writeIndexesForFile(
      fileIndexingResult: FileIndexingResult,
      statistics: IndexingFileSetStatistics,
      startTime: Long,
      length: Long,
      contentLoadingTime: Long,
    ) {

      val preparingTime = System.nanoTime() - startTime

      val indexWriter = IndexWriter.suitableWriter(fileIndexingResult.applicationMode, forceWriteSynchronously = false)
      indexWriter.writeAsync(fileIndexingResult) {
        synchronized(statistics) {
          val applicationTime = fileIndexingResult.applicationTimeNanos()
          statistics.addFileStatistics(fileIndexingResult.file(),
                                       fileIndexingResult.statistics(),
                                       preparingTime + applicationTime,
                                       contentLoadingTime,
                                       length,
                                       applicationTime
          )
        }
        releaseFile(fileIndexingResult.file())
      }
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
  }
}