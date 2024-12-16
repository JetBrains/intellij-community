// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.util.PingProgress
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.events.FileIndexingRequest
import it.unimi.dsi.fastutil.longs.LongArraySet
import it.unimi.dsi.fastutil.longs.LongSet
import it.unimi.dsi.fastutil.longs.LongSets
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

private class PerProviderSinkFactory() {
  private val activeSinksCount: AtomicInteger = AtomicInteger()
  private val cancelActiveSinks: AtomicBoolean = AtomicBoolean()

  inner class PerProviderSinkImpl(private val doAddFile: (VirtualFile) -> Unit) : PerProjectIndexingQueue.PerProviderSink {
    private var closed = false

    init {
      activeSinksCount.incrementAndGet()
    }

    override fun addFile(file: VirtualFile) {
      LOG.assertTrue(!closed, "Should not invoke 'addFile' after 'close'")
      if (cancelActiveSinks.get()) {
        ProgressManager.getGlobalProgressIndicator()?.cancel()
        ProgressManager.checkCanceled()
        LOG.error("Could not cancel file addition")
      }

      doAddFile(file)
    }

    override fun close() {
      if (!closed) {
        closed = true
        activeSinksCount.decrementAndGet()
      }
    }
  }

  fun newSink(addFile: (VirtualFile) -> Unit): PerProjectIndexingQueue.PerProviderSink {
    if (cancelActiveSinks.get()) {
      ProgressManager.getGlobalProgressIndicator()?.cancel()
      ProgressManager.checkCanceled()
      LOG.error("Could not cancel sink creation")
    }

    return PerProviderSinkImpl(addFile)
  }

  fun cancelAllProducersAndWait() {
    cancelActiveSinks.set(true)
    ProgressIndicatorUtils.awaitWithCheckCanceled {
      PingProgress.interactWithEdtProgress()
      LockSupport.parkNanos(50_000_000)
      activeSinksCount.get() == 0
    }
  }

  fun resumeProducers() {
    cancelActiveSinks.set(false)
  }

  companion object {
    private val LOG = logger<PerProviderSinkFactory>()
  }
}

@Internal
@Service(Service.Level.PROJECT)
class PerProjectIndexingQueue(private val project: Project) {
  /**
   *  Not thread safe. These classes are cheap to construct and use - don't share instances.
   *  <p>
   *  Always use try-with-resources when creating instances of this interface, otherwise [cancelAllTasksAndWait] may never end waiting
   */
  interface PerProviderSink : AutoCloseable {
    fun addFile(file: VirtualFile)
    override fun close()
  }

  private val sinkFactory = PerProviderSinkFactory()

  @Internal
  class QueuedFiles {
    // Files that will be re-indexed
    private val requestsSoFar: MutableStateFlow<PersistentSet<FileIndexingRequest>> = MutableStateFlow(persistentSetOf())

    internal val estimatedFilesCount: Flow<Int> = requestsSoFar.map { it.size }

    // Ids of scannings from which [filesSoFar] came
    internal val scanningIdsSoFar: LongSet = createSetForScanningIds()

    val size: Int get() = requestsSoFar.value.size

    val isEmpty: Boolean get() = requestsSoFar.value.isEmpty()

    val requests: Set<FileIndexingRequest> get() = requestsSoFar.value

    val scanningIds: LongSet get() = LongSets.unmodifiable(scanningIdsSoFar)

    internal fun addFile(file: VirtualFile, scanningId: Long) {
      scanningIdsSoFar.add(scanningId)
      requestsSoFar.update { it.add(FileIndexingRequest.updateRequest(file)) }
    }

    @Deprecated("Indexing should always start with scanning. You don't need this method - do scanning instead")
    internal fun addRequests(files: Collection<FileIndexingRequest>, scanningId: Collection<Long>) {
      scanningIdsSoFar.addAll(scanningId)
      requestsSoFar.update { it.addAll(files) }
    }

    fun asChannel(cs: CoroutineScope, bufferSize: Int): Channel<FileIndexingRequest> {
      val channel = Channel<FileIndexingRequest>(bufferSize)
      cs.async {
        try {
          requestsSoFar.value.forEach {
            channel.send(it)
          }
        }
        finally {
          channel.close()
        }
      }
      return channel
    }

    companion object {
      @Internal
      @JvmStatic
      @Deprecated("Indexing should always start with scanning. You don't need this method - do scanning instead")
      fun fromFilesCollection(files: Collection<VirtualFile>, scanningIds: Collection<Long>): QueuedFiles {
        return fromRequestsCollection(files.map(FileIndexingRequest::updateRequest), scanningIds)
      }

      @Internal
      @JvmStatic
      @Deprecated("Indexing should always start with scanning. You don't need this method - do scanning instead")
      fun fromRequestsCollection(files: Collection<FileIndexingRequest>, scanningIds: Collection<Long>): QueuedFiles {
        val res = QueuedFiles()
        res.addRequests(files, scanningIds)
        return res
      }
    }
  }

  private val queuedFiles: MutableStateFlow<QueuedFiles> = MutableStateFlow(QueuedFiles())
  private val queuedFilesLock: ReadWriteLock = ReentrantReadWriteLock()

  @Volatile
  private var allowFlushing: Boolean = true

  fun flushNow(reason: String): Boolean {
    if (!allowFlushing) {
      LOG.info("Flushing is not allowed at the moment")
      return false
    }
    val snapshot = getAndResetQueuedFiles()
    return if (snapshot.size > 0) {
      // note that DumbModeWhileScanningTrigger will not finish dumb mode until scanning is finished
      UnindexedFilesIndexer(project, snapshot, reason).queue(project)
      true
    }
    else {
      LOG.info("Finished for " + project.name + ". No files to index with loading content.")
      false
    }
  }

  fun clear() {
    getAndResetQueuedFiles()
  }

  @TestOnly
  fun <T> getFilesSubmittedDuring(block: () -> T): Pair<T, Collection<FileIndexingRequest>> {
    allowFlushing = false
    try {
      val result: T = block()
      return Pair(result, getAndResetQueuedFiles().requests)
    }
    finally {
      allowFlushing = true
    }
  }

  private fun getAndResetQueuedFiles(): QueuedFiles {
    return queuedFilesLock.writeLock().withLock {
      queuedFiles.getAndUpdate { QueuedFiles() }
    }
  }

  /**
   * Creates new instance of **thread-unsafe** [PerProviderSink]
   * Will throw [ProcessCanceledException] if the queue is suspended via [cancelAllTasksAndWait]
   */
  fun getSink(scanningId: Long): PerProviderSink {
    return sinkFactory.newSink { vFile ->
      // readLock here is to make sure that queuedFiles does not change during the operation
      queuedFilesLock.readLock().withLock {
        // .value for each file, because we want to put files into a new queue after getAndResetQueuedFiles invocation
        queuedFiles.value.addFile(vFile, scanningId)
      }
    }
  }

  /**
   * Cancels all the created [PerProviderSink] and waits until all the Sinks are finished (invoke [PerProviderSink.commit()]).
   * New invocations of [PerProjectIndexingQueue.getSink()] will throw [ProcessCanceledException].
   * Use [resumeQueue] to resume the queue.
   * Does nothing if the queue is already suspended.
   */
  fun cancelAllTasksAndWait() {
    sinkFactory.cancelAllProducersAndWait()
  }

  /**
   * Resumes the queue after [cancelAllTasksAndWait] invocation.
   * Does nothing if the queue is already resumed.
   */
  fun resumeQueue() {
    sinkFactory.resumeProducers()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun estimatedFilesCount(): Flow<Int> = queuedFiles.flatMapLatest { it.estimatedFilesCount }

  internal val scanningIndexingMutex = Mutex()

  @Internal
  internal fun wrapIndexing(indexingRoutine: Runnable) {
    runBlockingCancellable {
      scanningIndexingMutex.withLock("indexing", indexingRoutine::run)
    }
  }

  companion object {
    private val LOG = logger<PerProjectIndexingQueue>()
    private fun createSetForScanningIds(): LongSet = LongSets.synchronize(LongArraySet(4))
  }

  @TestOnly
  class TestCompanion(private val q: PerProjectIndexingQueue) {
    fun getAndResetQueuedFiles(): QueuedFiles {
      return q.getAndResetQueuedFiles()
    }
  }
}