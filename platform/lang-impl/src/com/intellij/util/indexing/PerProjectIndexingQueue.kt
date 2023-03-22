// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.PingProgress
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryImpl
import com.intellij.util.indexing.roots.IndexableFilesIterator
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private class PerProviderSinkFactory(private val uncommittedListener: UncommittedFilesListener) {
  interface UncommittedFilesListener {
    fun onUncommittedCountChanged(cntDirty: Int)
    fun commit(iterator: IndexableFilesIterator, files: List<VirtualFile>)
  }

  private val activeSinksCount: AtomicInteger = AtomicInteger()
  private val cancelActiveSinks: AtomicBoolean = AtomicBoolean()
  private val cntFilesDirty = AtomicInteger()

  inner class PerProviderSinkImpl(private val iterator: IndexableFilesIterator) : PerProjectIndexingQueue.PerProviderSink {
    private val files: MutableList<VirtualFile> = ArrayList()
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

      files.add(file)
      val cntDirty = cntFilesDirty.incrementAndGet()
      uncommittedListener.onUncommittedCountChanged(cntDirty)
    }

    override fun clear() {
      LOG.assertTrue(!closed, "Should not invoke 'clear' after 'close'")

      if (files.isNotEmpty()) {
        val cntDirty = cntFilesDirty.addAndGet(-files.size)
        LOG.assertTrue(cntDirty >= 0, "cntFilesDirty should be positive or 0: ${cntDirty}")
        files.clear()
        uncommittedListener.onUncommittedCountChanged(cntDirty)
      }
    }

    override fun commit() {
      LOG.assertTrue(!closed, "Should not invoke 'commit' after 'close'")

      if (files.isNotEmpty()) {
        val cntDirty = cntFilesDirty.addAndGet(-files.size)
        LOG.assertTrue(cntDirty >= 0, "cntFilesDirty should be positive or 0: ${cntDirty}")
        uncommittedListener.commit(iterator, files)
        files.clear()
      }
    }

    override fun close() {
      try {
        clear()
      }
      finally {
        closed = true
        activeSinksCount.decrementAndGet()
      }
    }
  }

  fun newSink(provider: IndexableFilesIterator): PerProjectIndexingQueue.PerProviderSink {
    if (cancelActiveSinks.get()) {
      ProgressManager.getGlobalProgressIndicator()?.cancel()
      ProgressManager.checkCanceled()
    }

    return PerProviderSinkImpl(provider)
  }

  fun cancelAllProducersAndWait() {
    cancelActiveSinks.set(true)
    ProgressIndicatorUtils.awaitWithCheckCanceled {
      PingProgress.interactWithEdtProgress()
      LockSupport.parkNanos(50_000_000)
      activeSinksCount.get() == 0
    }
    LOG.assertTrue(cntFilesDirty.get() == 0, "Should contain no dirty files. But got: " + cntFilesDirty.get())
  }

  fun resumeProducers() {
    cancelActiveSinks.set(false)
  }

  companion object {
    private val LOG = logger<PerProviderSinkFactory>()
  }
}

@Service(Service.Level.PROJECT)
class PerProjectIndexingQueue(private val project: Project) : Disposable {
  /**
   *  Not thread safe. These classes are cheap to construct and use - don't share instances.
   *  <p>
   *  Invoke [commit] to commit the changes. All the uncommitted changes will be lost after invocation of [close].
   *  <p>
   *  Always use try-with-resources when creating instances of this interface, otherwise [cancelAllTasksAndWait] may never end waiting
   */
  interface PerProviderSink : AutoCloseable {
    fun addFile(file: VirtualFile)
    fun clear()
    fun commit()
    override fun close()
  }

  private val sinkFactory = PerProviderSinkFactory(object : PerProviderSinkFactory.UncommittedFilesListener {
    private val shouldScanInSmartMode = UnindexedFilesScanner.shouldScanInSmartMode()

    override fun onUncommittedCountChanged(cntDirty: Int) {
      if (shouldScanInSmartMode) startOrStopDumbModeIfManyOrFewFilesAreDirty(cntDirty)
      // don't start DumbModeWhileScanning when scanning in smart mode is disabled. Otherwise, due to merged and cancelled tasks,
      // scanning task may appear later in the queue than DumbModeWhileScanning, which effectively means a deadlock
      // (DumbModeWhileScanning waits for a latch that will be counted down in scanning task via PerProjectIndexingQueue.flush)
    }

    override fun commit(iterator: IndexableFilesIterator, files: List<VirtualFile>) = addFiles(iterator, files)
  })

  // guarded by [lock]. Must be in consistent state under write lock (see [lock] comment)
  // Used by dumb mode tasks to wait for flush (which is happening, for example, in the end of initial project scanning)
  private val scanningLatch: AtomicReference<CountDownLatch?> = AtomicReference()

  // guarded by [lock]. Must be in consistent state under write lock (see [lock] comment)
  // Total count of VirtualFile in filesSoFar. This is (arguable) performance optimization
  private val cntFilesSoFar = AtomicInteger()

  // guarded by [lock]. Must be in consistent state under write lock (see [lock] comment)
  // Files that will be re-indexed
  private var filesSoFar: ConcurrentMap<IndexableFilesIterator, Collection<VirtualFile>> = ConcurrentHashMap()

  // Code under read lock still runs in parallel, so all the counters (e.g. [cntFilesSoFar]) and collections (e.g. [filesSoFar]) still have
  // to be thread-safe. It is only required that the state must be consistent under write lock (e.g. [cntFilesSoFar] corresponds to total
  // count of files in [filesSoFar])
  private val lock = ReentrantReadWriteLock()

  override fun dispose() {
    scanningLatch.get()?.countDown()
  }

  // `private` because we want clients to use [PerProviderSink] which forces them to report files one by one.
  // Accepting `List<VirtualFile>` delays the moment when we know that many files have changed, and we need a dumb mode.
  // Accepting [VirtualFile] without intermediate buffering ([PerProviderSink] is essentially a non-thread safe buffer) and adding
  // them directly to [filesSoFar] will likely slow down the process (though this assumption is not properly verified)
  private fun addFiles(iterator: IndexableFilesIterator, files: List<VirtualFile>) {
    lock.read {
      filesSoFar.compute(iterator) { _, old ->
        return@compute if (old == null) ArrayList(files) else old + files
      }
      cntFilesSoFar.addAndGet(files.size)
    }
  }

  private fun startOrStopDumbModeIfManyOrFewFilesAreDirty(cntUncommittedDirty: Int) {
    lock.read {
      if (cntUncommittedDirty + cntFilesSoFar.get() >= DUMB_MODE_THRESHOLD) {
        if (scanningLatch.get() != null) return // already started
        val latch = CountDownLatch(1)
        if (scanningLatch.compareAndSet(null, latch)) {
          DumbModeWhileScanning(latch, scanningLatch, project).queue(project)
        }
      }
      else {
        val latch = scanningLatch.get()
        if (latch != null && scanningLatch.compareAndSet(latch, null)) {
          // finish started dumb mode. For example, scanning task was canceled, uncommitted [PerProviderSink] were cleared and dumb mode is
          // not needed anymore
          latch.countDown()
        }
      }
      // otherwise do nothing
    }
  }

  fun flushNow(reason: String) {
    val (filesInQueue, totalFiles, currentLatch) = getAndResetQueuedFiles()
    try {
      if (totalFiles > 0) {
        UnindexedFilesIndexer(project, filesInQueue, reason).queue(project)
      }
      else {
        LOG.info("Finished for " + project.name + ". No files to index with loading content.")
      }
    } finally {
      currentLatch?.countDown()
    }
  }

  fun flushNowSync(projectIndexingHistory: ProjectIndexingHistoryImpl, indicator: ProgressIndicator) {
    val (filesInQueue, totalFiles, currentLatch) = getAndResetQueuedFiles()
    try {
      if (totalFiles > 0) {
        val indexingReason = projectIndexingHistory.indexingReason ?: "Flushing queue of project ${project.name}"
        UnindexedFilesIndexer(project, filesInQueue, indexingReason).indexFiles(projectIndexingHistory, indicator)
      }
      else {
        LOG.info("Finished for " + project.name + ". No files to index with loading content.")
      }
    } finally {
      currentLatch?.countDown()
    }
  }

  fun clear() {
    val (_, _, currentLatch) = getAndResetQueuedFiles()
    currentLatch?.countDown() // release DumbModeWhileScanning if it has already been queued or running
  }

  private fun getAndResetQueuedFiles(): Triple<ConcurrentMap<IndexableFilesIterator, Collection<VirtualFile>>, Int, CountDownLatch?> {
    return lock.write {
      val filesInQueue = filesSoFar
      filesSoFar = ConcurrentHashMap()
      val totalFiles = cntFilesSoFar.getAndSet(0)
      val currentLatch = scanningLatch.getAndSet(null)
      return@write Triple(filesInQueue, totalFiles, currentLatch)
    }
  }

  /**
   * Creates new instance of **thread-unsafe** [PerProviderSink]
   * Will throw [ProcessCanceledException] if the queue is suspended via [cancelAllTasksAndWait]
   */
  fun getSink(provider: IndexableFilesIterator): PerProviderSink {
    return sinkFactory.newSink(provider)
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

  companion object {
    private val LOG = logger<PerProjectIndexingQueue>()
    private val DUMB_MODE_THRESHOLD: Int by lazy { Registry.intValue("scanning.dumb.mode.threshold", 20) }
  }

  private class DumbModeWhileScanning(private val latch: CountDownLatch,
                                      private val latchRef: AtomicReference<CountDownLatch?>,
                                      private val project: Project) : DumbModeTask() {
    override fun performInDumbMode(indicator: ProgressIndicator) {
      indicator.isIndeterminate = true
      indicator.text = IndexingBundle.message("progress.indexing.waiting.for.scanning.to.complete")

      ProgressIndicatorUtils.awaitWithCheckCanceled(latch)

      // also wait for all the other scanning tasks to complete before starting indexing tasks
      ProgressIndicatorUtils.awaitWithCheckCanceled {
        LockSupport.parkNanos(50_000_000)
        return@awaitWithCheckCanceled !project.service<UnindexedFilesScannerExecutor>().isRunning.value
      }
    }

    override fun dispose() {
      // This task is not running anymore. For example, it can be cancelled (e.g. by DumbService.cancelAllTasksAndWait())
      // Let the outer PerProjectIndexingQueue know about that
      latchRef.compareAndSet(latch, null)
    }
  }

  @TestOnly
  class TestCompanion(private val q: PerProjectIndexingQueue) {
    fun getAndResetQueuedFiles(): Triple<ConcurrentMap<IndexableFilesIterator, Collection<VirtualFile>>, Int, CountDownLatch?> {
      return q.getAndResetQueuedFiles()
    }
  }
}