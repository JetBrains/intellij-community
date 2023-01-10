// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryImpl
import com.intellij.util.indexing.roots.IndexableFilesIterator
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private class PerProviderSinkFactory(private val uncommittedListener: UncommittedFilesListener) {
  interface UncommittedFilesListener {
    fun onUncommittedCountChanged(cntDirty: Int)
    fun commit(iterator: IndexableFilesIterator, files: List<VirtualFile>)
  }

  private val cntFilesDirty = AtomicInteger()

  inner class PerProviderSinkImpl(private val iterator: IndexableFilesIterator) : PerProjectIndexingQueue.PerProviderSink {
    private val files: MutableList<VirtualFile> = ArrayList()
    private var committed = false

    override fun addFile(file: VirtualFile) {
      LOG.assertTrue(!committed, "Should not invoke 'addFile' after 'commit'")

      files.add(file)
      val cntDirty = cntFilesDirty.incrementAndGet()
      uncommittedListener.onUncommittedCountChanged(cntDirty)
    }

    override fun clear() {
      val cntDirty = cntFilesDirty.addAndGet(-files.size)
      files.clear()
      uncommittedListener.onUncommittedCountChanged(cntDirty)
    }

    override fun commit() {
      committed = true
      if (files.isNotEmpty()) {
        cntFilesDirty.addAndGet(-files.size)
        uncommittedListener.commit(iterator, files)
      }
    }
  }

  fun newSink(provider: IndexableFilesIterator): PerProjectIndexingQueue.PerProviderSink {
    return PerProviderSinkImpl(provider)
  }

  companion object {
    private val LOG = logger<PerProviderSinkFactory>()
  }
}

@Service(Service.Level.PROJECT)
class PerProjectIndexingQueue(private val project: Project) : Disposable {
  /** Not thread safe */
  interface PerProviderSink {
    fun addFile(file: VirtualFile)
    fun clear()
    fun commit()
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
        return@compute if (old == null) files else old + files
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
          DumbModeWhileScanning(latch, scanningLatch).queue(project)
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

  fun flushNow() {
    val (filesInQueue, totalFiles, currentLatch) = getAndResetQueuedFiles()
    if (totalFiles > 0) {
      UnindexedFilesIndexer(project, filesInQueue).queue(project)
      currentLatch?.countDown()
    }
    else {
      LOG.info("Finished for " + project.name + ". No files to index with loading content.")
    }
  }

  fun flushNowSync(projectIndexingHistory: ProjectIndexingHistoryImpl, indicator: ProgressIndicator) {
    val (filesInQueue, totalFiles, currentLatch) = getAndResetQueuedFiles()
    if (totalFiles > 0) {
      UnindexedFilesIndexer(project, filesInQueue).indexFiles(projectIndexingHistory, indicator)
      currentLatch?.countDown()
    }
    else {
      LOG.info("Finished for " + project.name + ". No files to index with loading content.")
    }
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

  fun getSink(provider: IndexableFilesIterator): PerProviderSink {
    return sinkFactory.newSink(provider)
  }

  companion object {
    private val LOG = logger<PerProjectIndexingQueue>()
    private const val DUMB_MODE_THRESHOLD: Int = 20
  }

  private class DumbModeWhileScanning(private val latch: CountDownLatch,
                                      private val latchRef: AtomicReference<CountDownLatch?>) : DumbModeTask() {
    override fun performInDumbMode(indicator: ProgressIndicator) {
      indicator.isIndeterminate = true
      indicator.text = IndexingBundle.message("progress.indexing.waiting.for.scanning.to.complete")

      ProgressIndicatorUtils.awaitWithCheckCanceled(latch)
    }

    override fun dispose() {
      // This task is not running anymore. For example, it can be cancelled (e.g. by DumbService.cancelAllTasksAndWait())
      // Let the outer PerProjectIndexingQueue know about that
      latchRef.compareAndSet(latch, null)
    }
  }
}