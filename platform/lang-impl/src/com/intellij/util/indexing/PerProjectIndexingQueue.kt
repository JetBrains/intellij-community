// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.PingProgress
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.roots.IndexableFilesIterator
import it.unimi.dsi.fastutil.longs.LongArraySet
import it.unimi.dsi.fastutil.longs.LongSet
import it.unimi.dsi.fastutil.longs.LongSets
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

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

@ApiStatus.Internal
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

  // Files that will be re-indexed
  private val filesSoFar: MutableStateFlow<PersistentSet<VirtualFile>> = MutableStateFlow(persistentSetOf())

  private val estimatedFilesCount: Flow<Int> = filesSoFar.map { it.size }

  // Ids of scannings from which [filesSoFar] came
  private var scanningIds: LongSet = createSetForScanningIds()

  @Volatile
  private var allowFlushing: Boolean = true

  fun flushNow(reason: String) {
    if (!allowFlushing) {
      LOG.info("Flushing is not allowed at the moment")
      return
    }
    val (filesInQueue, scanningIds) = getAndResetQueuedFiles()
    if (filesInQueue.size > 0) {
      // note that DumbModeWhileScanningTrigger will not finish dumb mode until scanning is finished
      UnindexedFilesIndexer(project, filesInQueue, reason, scanningIds).queue(project)
    }
    else {
      LOG.info("Finished for " + project.name + ". No files to index with loading content.")
    }
  }

  fun clear() {
    getFilesAndClear()
  }

  @VisibleForTesting
  fun <T> getFilesSubmittedDuring(block: () -> T): Pair<T, Collection<VirtualFile>> {
    allowFlushing = false
    try {
      val result: T = block()
      return Pair(result, getFilesAndClear())
    }
    finally {
      allowFlushing = true
    }
  }

  private fun getFilesAndClear(): Collection<VirtualFile> {
    val files = getAndResetQueuedFiles()
    return files.fileSet
  }

  private data class QueuedFiles(
    val fileSet: Set<VirtualFile>,
    val scanningIds: LongSet
  )

  private fun getAndResetQueuedFiles(): QueuedFiles {
    val filesInQueue = filesSoFar.getAndUpdate { persistentSetOf() }
    val idsOfScannings = scanningIds // we only use scanning ids for diagnostics. TODO: maybe fix non-atomic update
    scanningIds = createSetForScanningIds()
    return QueuedFiles(filesInQueue,  LongSets.unmodifiable(idsOfScannings))
  }

  /**
   * Creates new instance of **thread-unsafe** [PerProviderSink]
   * Will throw [ProcessCanceledException] if the queue is suspended via [cancelAllTasksAndWait]
   */
  fun getSink(provider: IndexableFilesIterator, scanningId: Long): PerProviderSink {
    scanningIds.add(scanningId)
    return sinkFactory.newSink { vFile -> filesSoFar.update { it.add(vFile) } }
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

  fun estimatedFilesCount(): Flow<Int> = estimatedFilesCount

  companion object {
    private val LOG = logger<PerProjectIndexingQueue>()
    private fun createSetForScanningIds(): LongSet = LongSets.synchronize(LongArraySet(4))
  }

  @TestOnly
  class TestCompanion(private val q: PerProjectIndexingQueue) {
    fun getAndResetQueuedFiles(): Pair<Set<VirtualFile>, Int> {
      return q.getAndResetQueuedFiles().let { Pair(it.fileSet, it.fileSet.size) }
    }
  }
}