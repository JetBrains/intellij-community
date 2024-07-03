// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.contentQueue

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingCancellable
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
import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.platform.diagnostic.telemetry.Indexes
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

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

    runBlockingCancellable {

      //Detach UI update from processing: it's a waste of CPU to update UI on each
      // file processed, especially if the machine is powerful with a lot of CPU cores
      //TODO RC: all this is better encapsulated in the IndexingProgressReporter itself
      val currentlyIndexedFileRef: AtomicReference<VirtualFile?> = AtomicReference(null)
      val filesIndexed = AtomicInteger(0)
      val progressReportingJob = launch {
        while (true) {
          delay(200)
          val currentlyIndexedFile = currentlyIndexedFileRef.get()
          if (currentlyIndexedFile != null) {
            val presentableLocation = getPresentableLocationBeingIndexed(project, currentlyIndexedFile)
            progressReporter.setLocationBeingIndexed(presentableLocation)
          }
          progressReporter.filesProcessed(filesIndexed.getAndSet(0))
        }
      }



      processFileSetInParallel(project, fileSet) { fileIndexingRequest ->
        currentlyIndexedFileRef.set(fileIndexingRequest.file)

        indexOneFileHandleExceptions(fileIndexingRequest, project, project, contentLoader, fileSet.statistics)

        filesIndexed.incrementAndGet()
      }

      progressReportingJob.cancel()
    }
  }

  private suspend fun processFileSetInParallel(
    project: Project,
    fileSet: FileSet,
    processRequestTask: suspend (FileIndexingRequest) -> Unit,
  ) {

    TRACER.spanBuilder("doIndexFiles").setAttribute("files", fileSet.size().toLong()).useWithScope {
      withContext(Dispatchers.Default + CoroutineName("Indexing(${project.locationHash}")) {
        //Ideally, we should launch a coroutine for each file in a fileSet, and let the coroutine scheduler do it's job
        // of distributing the load across available CPUs.
        // But the fileSet could be quite large (10-100-1000k files), so it could be quite a load for a scheduler.
        // So an optimization: we use fixed number of coroutines (approx. = # available CPUs), and a channel:
        // BTW .forEachConcurrent(concurrency = INDEXING_PARALLELIZATION) does almost the same thing, but it uses
        // channel(size: 0), i.e. rendezvous-channel. I setup channel(size: 8k), to have some buffering:

        val bufferSize = fileSet.filesOriginal.size.coerceIn(INDEXING_PARALLELIZATION, 8192)
        val channel = fileSet.filesOriginal.asChannel(this, bufferSize = bufferSize)
        repeat(INDEXING_PARALLELIZATION) { workerNo ->
          launch {
            try {
              for (fileIndexingRequest in channel) {
                while (fileSet.shouldPause()) { // TODO: get rid of legacy suspender
                  delay(1)
                }

                TRACER.spanBuilder("indexOneFile")
                  .setAttribute("f", fileIndexingRequest.file.name)
                  .setAttribute("i", workerNo.toLong())
                  .useWithScope {
                    processRequestTask(fileIndexingRequest)
                  }

                ensureActive()
              }
            }
            //FIXME RC: for profiling, remove afterwards
            catch (e: Throwable) {
              LOG.info("Coroutine $workerNo finished exceptionally", e)
              throw e
            }
            finally {
              LOG.info("Coroutine $workerNo finished gracefully")
            }
          }
        }
      }
      //TODO RC: assumed implicit knowledge that defaultParallelWriter is the writer responsible for the
      //         index writing down the stack. But what if it is not?
      IndexWriter.defaultParallelWriter().waitCurrentIndexingToFinish()
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
    catch (e: CancellationException) {
      FileBasedIndexImpl.LOG.info("Indexing coroutine cancelled")
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

    private suspend fun getApplierForFileIndexDelete(
      indexingStamp: FileIndexingStamp,
      file: VirtualFile, parentDisposable: Disposable,
    ): FileIndexingResult? {
      //TODO RC: do we need parentDisposable in coroutine world -- seems like scoping should deal with
      //         the lifecycle?
      val fileIndexingResult = readAction {
        fileBasedIndex.getApplierToRemoveDataFromIndexesForFile(file, indexingStamp)
      }
      incIndexingSuccessfulCountAndLogIfNeeded()
      return fileIndexingResult
    }

    private suspend fun getApplierForFileIndexUpdate(
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
        //TODO RC: do we need parentDisposable in coroutine world -- seems like scoping should deal with
        //         the lifecycle?
        val fileIndexingResult = readAction {
          indexingAttemptCount.incrementAndGet()
          val fileType = if (fileTypeChangeChecker.get()) type else null
          fileBasedIndex.indexFileContent(project, fileContent, false, fileType, indexingStamp)
        }
        incIndexingSuccessfulCountAndLogIfNeeded()
        return Triple(fileIndexingResult, contentLoadingTime, length)
      }
      finally {
        loadedFileContentLimiter.releaseBytes(length)
      }
    }

    @Throws(TooLargeContentException::class, FailedToLoadContentException::class)
    private suspend fun loadContent(
      file: VirtualFile,
      loader: CachedFileContentLoader,
    ): ContentLoadingResult {
      if (fileBasedIndex.isTooLarge(file)) {
        throw TooLargeContentException(file)
      }

      val fileLength: Long = try {
        file.length
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        throw FailedToLoadContentException(file, e)
      }

      // Reserve bytes for the file.
      return loadedFileContentLimiter.acquiringBytes(fileLength) {
        //TODO RC: withContext(Dispatchers.IO) ?
        //TODO RC: non-cancellable section is used just to avoid context-switch down the stack (DiskQueryRelay):
        //TODO RC: withContext(NonCancellable) {} don't work here (but should work: IJPL-157558)
        Cancellation.withNonCancelableSection().use {
          val fileContent = loader.loadContent(file)
          ContentLoadingResult(fileContent, fileLength)
        }
      }
    }
  }

  @JvmRecord
  private data class ContentLoadingResult(val cachedFileContent: CachedFileContent, val fileLength: Long)

  companion object {
    internal val LOG = Logger.getInstance(IndexUpdateRunner::class.java)

    private val VERBOSE_INDEXES: Scope = Scope(Indexes.name, Indexes.parent, verbose = true)

    internal val TRACER: IJTracer = TelemetryManager.getTracer(VERBOSE_INDEXES)

    /** Number indexing tasks to run in parallel */
    val INDEXING_PARALLELIZATION: Int = UnindexedFilesUpdater.getMaxNumberOfIndexingThreads()

    /**
     * Soft cap of memory we are using for loading files content during indexing process.
     * Single file may be bigger, but until memory is freed indexing is suspended.
     * @see UsedMemorySoftLimiter
     */
    private val SOFT_MAX_TOTAL_BYTES_LOADED_INTO_MEMORY = INDEXING_PARALLELIZATION * 4L * FileUtilRt.MEGABYTE

    private val loadedFileContentLimiter = UsedMemorySoftLimiter(SOFT_MAX_TOTAL_BYTES_LOADED_INTO_MEMORY)

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

/**
 * Limit total memory used, to prevent OutOfMemory.
 * Used for limiting file contents loading.
 *
 * Limiter is 'soft' which means it allows one (and only one) [acquireBytes] operation exceed this limit.
 *
 * Motivation: we want to limit total amount of memory occupied by files content in any given moment of indexing.
 * But at the same time it could be the file to index is so huge, so than that its size is larger than the limit
 * alone -- and we still want to be able to index such files, so the Limiter must allow them too.
 *
 * BTW: usually file size to index is limited by [FileBasedIndexImpl.isTooLarge], but the limit there is quite large
 * (~20Mb), and also there are file types for which the limit is ignored, i.e. there is basically no limit
 *
 * So in its maximum we will load `softLimitOfTotalBytesUsed + max(file size)`, which seems acceptable, because we have
 * to index this "max(file size)" file anyway (even if its size is 4 Gb), and `softLimitOfTotalBytesUsed` additional
 * bytes are insignificant.
 */
private class UsedMemorySoftLimiter(private val softLimitOfTotalBytesUsed: Long) {

  private val totalBytesUsed: AtomicLong = AtomicLong(0)

  /**
   * Runs block of code, acquiring [bytesToAcquire], but releases those bytes if the block of code fails, i.e.
   * throws any exception.
   */
  suspend inline fun <T> acquiringBytes(bytesToAcquire: Long, block: () -> T): T {
    acquireBytes(bytesToAcquire)
    try {
      return block()
    }
    catch (e: Throwable) {
      releaseBytes(bytesToAcquire)
      throw e
    }
  }

  suspend fun acquireBytes(bytesToAcquire: Long) {
    while (true) { //CAS-loop
      val _totalBytesUsed = totalBytesUsed.get()
      if (_totalBytesUsed > softLimitOfTotalBytesUsed) {
        yield()
        continue
      }

      if (totalBytesUsed.compareAndSet(_totalBytesUsed, _totalBytesUsed + bytesToAcquire)) {
        return
      }
    }
  }

  fun releaseBytes(bytesToRelease: Long) {
    val totalBytesInUse = totalBytesUsed.addAndGet(-bytesToRelease)
    check(totalBytesInUse >= 0) { "Total bytes in use ($totalBytesInUse) is negative" }
  }
}
