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

@Internal
@Service(Service.Level.PROJECT)
class PerProjectIndexingQueue(private val project: Project) {

  @Internal
  class QueuedFiles {
    // Files that will be re-indexed
    private val requestsSoFar: MutableStateFlow<PersistentSet<FileIndexingRequest>> = MutableStateFlow(persistentSetOf())

    internal fun currentFilesCount(): Int = requestsSoFar.value.size

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

    val queuedFilesCount = queuedFilesLock.readLock().withLock {
      // note: read lock only ensures that reference to queuedFiles does change during the operation,
      // so we can safely invoke queuedFiles.value. List of queued files may change (currentFilesCount may change)
      // In order to prevent currentFilesCount changing, we need a write lock. At the moment this is not a problem, because
      // currentFilesCount may only grow, and it is expected that this number may change immediately after we release the lock.
      queuedFiles.value.currentFilesCount()
    }

    return if (queuedFilesCount > 0) {
      // note that DumbModeWhileScanningTrigger will not finish dumb mode until scanning is finished
      UnindexedFilesIndexer(project, reason).queue(project)
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
  fun <T> disableFlushingDuring(block: () -> T): T {
    allowFlushing = false
    try {
      return block()
    }
    finally {
      allowFlushing = true
    }
  }

  @TestOnly
  fun getQueuedFiles(): QueuedFiles {
    return queuedFilesLock.readLock().withLock {
      queuedFiles.value
    }
  }

  internal fun getAndResetQueuedFiles(): QueuedFiles {
    return queuedFilesLock.writeLock().withLock {
      queuedFiles.getAndUpdate { QueuedFiles() }
    }
  }

  /**
   * Creates new instance of **thread-unsafe** [PerProviderSink]
   * Will throw [ProcessCanceledException] if the queue is suspended via [cancelAllTasksAndWait]
   */
  fun addFile(vFile: VirtualFile, scanningId: Long) {
    // readLock here is to make sure that queuedFiles does not change during the operation
    queuedFilesLock.readLock().withLock {
      // .value for each file, because we want to put files into a new queue after getAndResetQueuedFiles invocation
      queuedFiles.value.addFile(vFile, scanningId)
    }
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