// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events

import com.intellij.history.LocalHistory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbServiceImpl.Companion.isSynchronousTaskExecution
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexImpl
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.createBoundedTaskExecutor
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.FileBasedIndexProjectHandler
import com.intellij.util.indexing.IndexUpToDateCheckIn.isUpToDateCheckEnabled
import com.intellij.util.indexing.IndexingStamp
import com.intellij.util.indexing.events.VfsEventsMerger.VfsEventProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.*
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Lock
import kotlin.concurrent.withLock

private val LOG = logger<ChangedFilesCollector>()

private const val MIN_CHANGES_TO_PROCESS_ASYNC = 20

/**
 * Collects file changes supplied from VFS via [AsyncFileListener] into [eventMerger].
 * Collected changes could be accessed via [getEventMerger]
 */
@ApiStatus.Internal
class ChangedFilesCollector internal constructor(coroutineScope: CoroutineScope) : IndexedFilesListener() {
  /** The projects for the file are taken from fileBasedIndex.indexableFilesFilterHolder */
  val dirtyFiles: DirtyFiles = DirtyFiles()

  private val processedEventIndex = AtomicInteger()

  private val workersFinishedSync: Phaser = object : Phaser() {
    override fun onAdvance(phase: Int, registeredParties: Int): Boolean = false
  }

  /** Used in [ensureUpToDateAsync] to process changes asynchronously */
  private val vfsEventsExecutor = createBoundedTaskExecutor("FileBasedIndex Vfs Event Processor", coroutineScope)
  private val scheduledVfsEventsWorkers = AtomicInteger()
  private val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl

  override fun iterateIndexableFiles(file: VirtualFile, iterator: ContentIterator) {
    if (fileBasedIndex.belongsToIndexableFiles(file)) {
      VfsUtilCore.visitChildrenRecursively(file, object : VirtualFileVisitor<Void?>() {
        override fun visitFile(file: VirtualFile): Boolean {
          if (!fileBasedIndex.belongsToIndexableFiles(file)) {
            return false
          }
          iterator.processFile(file)
          return true
        }
      })
    }
  }

  fun clear() {
    dirtyFiles.clear()
    ApplicationManager.getApplication().runReadAction {
      if (ApplicationManager.getApplication() == null) {
        // If the application is already disposed (ApplicationManager.getApplication() == null)
        // it means that this method is invoked via ShutDownTracker and the process will be shut down
        // so we don't need to clear collectors.
        return@runReadAction
      }
      processFilesInReadAction { true }
    }
  }

  override fun recordFileEvent(fileOrDir: VirtualFile, onlyContentDependent: Boolean) {
    addToDirtyFiles(fileOrDir)
    super.recordFileEvent(fileOrDir, onlyContentDependent)
  }

  override fun recordFileRemovedEvent(file: VirtualFile) {
    addToDirtyFiles(file)
    super.recordFileRemovedEvent(file)
  }

  private fun addToDirtyFiles(fileOrDir: VirtualFile) {
    if (fileOrDir !is VirtualFileWithId) {
      return
    }

    val fileId = fileOrDir.getId()
    val projects = fileBasedIndex.indexableFilesFilterHolder.findProjectsForFile(fileId)
    dirtyFiles.addFile(projects, fileId)
  }

  override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier {
    val shouldCleanup = events.any { event -> memoryStorageCleaningNeeded(event) }
    val superApplier = super.prepareChange(events)

    return object : AsyncFileListener.ChangeApplier {
      override fun beforeVfsChange() {
        if (shouldCleanup) {
          fileBasedIndex.cleanupMemoryStorage(false)
        }
        superApplier.beforeVfsChange()
      }

      override fun afterVfsChange() {
        superApplier.afterVfsChange()
        val registeredIndexes = fileBasedIndex.registeredIndexes
        if (registeredIndexes != null && registeredIndexes.isInitialized) {
          ensureUpToDateAsync()
        }
      }
    }
  }

  fun ensureUpToDate() {
    if (!isUpToDateCheckEnabled()) {
      return
    }

    //assert ApplicationManager.getApplication().isReadAccessAllowed() || ShutDownTracker.isShutdownHookRunning();
    fileBasedIndex.waitUntilIndicesAreInitialized()

    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      processFilesToUpdateInReadAction()
    }
    else {
      processFilesInReadActionWithYieldingToWriteAction()
    }
  }

  fun ensureUpToDateAsync() {
    if (eventMerger.approximateChangesCount < MIN_CHANGES_TO_PROCESS_ASYNC || !scheduledVfsEventsWorkers.compareAndSet(0, 1)) {
      return
    }

    if (isSynchronousTaskExecution) {
      ensureUpToDate()
      return
    }

    vfsEventsExecutor.execute {
      try {
        processFilesInReadActionWithYieldingToWriteAction()

        if (Registry.`is`("try.starting.dumb.mode.where.many.files.changed")) {
          for (project in ProjectManager.getInstance().getOpenProjects()) {
            try {
              FileBasedIndexProjectHandler.scheduleReindexingInDumbMode(project)
            }
            catch (@Suppress("IncorrectCancellationExceptionHandling") _: ProcessCanceledException) {
            }
            catch (e: Exception) {
              LOG.error(e)
            }
          }
        }
      }
      finally {
        scheduledVfsEventsWorkers.decrementAndGet()
      }
    }
  }

  fun processFilesToUpdateInReadAction() {
    processFilesInReadAction(object : VfsEventProcessor {
      private val perFileElementTypeUpdateProcessor = (StubIndex.getInstance() as StubIndexImpl)
        .perFileElementTypeModificationTrackerUpdateProcessor

      override fun process(info: VfsEventsMerger.ChangeInfo): Boolean {
        LOG.debug("Processing ", info)
        val fileId = info.fileId
        try {
          val file = info.file
          val dirtyQueueProjects = dirtyFiles.getProjects(fileId)
          if (info.isTransientStateChanged) {
            fileBasedIndex.doTransientStateChangeForFile(fileId, file, dirtyQueueProjects)
          }
          if (info.isContentChanged) {
            fileBasedIndex.scheduleFileForIncrementalIndexing(fileId, file, /*onlyContentChanged: */true, dirtyQueueProjects)
          }
          if (info.isFileRemoved) {
            fileBasedIndex.doInvalidateIndicesForFile(fileId, file, /*containingProjects: */emptySet(), dirtyQueueProjects)
          }
          if (info.isFileAdded) {
            fileBasedIndex.scheduleFileForIncrementalIndexing(fileId, file, /*onlyContentChanged: */false, dirtyQueueProjects)
          }


          if (StubIndexImpl.PER_FILE_ELEMENT_TYPE_STUB_CHANGE_TRACKING_SOURCE ==
            StubIndexImpl.PerFileElementTypeStubChangeTrackingSource.ChangedFilesCollector) {
            perFileElementTypeUpdateProcessor.processUpdate(file)
          }
        }
        catch (t: Throwable) {
          if (LOG.isDebugEnabled()) {//FIXME RC: what if it is PCE? -- must be re-thrown without logging
            LOG.debug("Exception while processing $info", t)
          }
          throw t
        }
        finally {
          dirtyFiles.removeFile(fileId)
        }
        return true
      }

      override fun endBatch() {
        if (StubIndexImpl.PER_FILE_ELEMENT_TYPE_STUB_CHANGE_TRACKING_SOURCE ==
          StubIndexImpl.PerFileElementTypeStubChangeTrackingSource.ChangedFilesCollector) {
          perFileElementTypeUpdateProcessor.endUpdatesBatch()
        }
      }
    })
  }

  private fun processFilesInReadAction(processor: VfsEventProcessor) {
    ThreadingAssertions.assertReadAccess()

    val publishedEventIndex = eventMerger.publishedEventIndex
    val processedEventIndex = processedEventIndex.get()
    if (processedEventIndex == publishedEventIndex) {
      return
    }

    workersFinishedSync.register()
    val phase = workersFinishedSync.phase
    try {
      fileBasedIndex.waitUntilIndicesAreInitialized()
      eventMerger.processChanges(object : VfsEventProcessor {
        override fun process(changeInfo: VfsEventsMerger.ChangeInfo): Boolean {
          withLock(fileBasedIndex.writeLock) {
            try {
              ProgressManager.getInstance().executeNonCancelableSection {
                processor.process(changeInfo)
              }
            }
            finally {
              IndexingStamp.flushCache(changeInfo.fileId)
            }
          }
          return true
        }

        override fun endBatch() {
          withLock(fileBasedIndex.writeLock) {
            processor.endBatch()
          }
        }
      })
    }
    finally {
      workersFinishedSync.arriveAndDeregister()
    }

    try {
      awaitWithCheckCancelled(workersFinishedSync, phase)
    }
    catch (e: RejectedExecutionException) {
      LOG.warn(e)
      throw ProcessCanceledException(e)
    }
    catch (e: InterruptedException) {
      LOG.warn(e)
      throw ProcessCanceledException(e)
    }

    if (eventMerger.publishedEventIndex == publishedEventIndex) {
      this.processedEventIndex.compareAndSet(processedEventIndex, publishedEventIndex)
    }
  }

  private fun processFilesInReadActionWithYieldingToWriteAction() {
    while (eventMerger.hasChanges()) {
      ReadAction.nonBlocking(Callable {
        processFilesToUpdateInReadAction()
        null
      }).executeSynchronously()
    }
  }

  @TestOnly
  fun waitForVfsEventsExecuted(timeout: Long, unit: TimeUnit, dispatchAllInvocationEvents: () -> Unit) {
    if (!ApplicationManager.getApplication().isDispatchThread()) {
      vfsEventsExecutor.waitAllTasksExecuted(timeout, unit)
      return
    }

    val deadline = System.nanoTime() + unit.toNanos(timeout)
    while (System.nanoTime() < deadline) {
      try {
        vfsEventsExecutor.waitAllTasksExecuted(100, TimeUnit.MILLISECONDS)
        return
      }
      catch (_: TimeoutCancellationException) {
        dispatchAllInvocationEvents()
      }
    }
  }
}

// we want to see method in stack traces
private fun withLock(lock: Lock, runnable: () -> Unit) {
  lock.withLock { runnable() }
}

private fun memoryStorageCleaningNeeded(event: VFileEvent): Boolean {
  val requestor = event.requestor
  return requestor is FileDocumentManager || requestor is PsiManager || requestor === LocalHistory.VFS_EVENT_REQUESTOR
}

@Throws(InterruptedException::class)
private fun awaitWithCheckCancelled(phaser: Phaser, phase: Int) {
  while (true) {
    ProgressManager.checkCanceled()
    try {
      phaser.awaitAdvanceInterruptibly(phase, 100, MILLISECONDS)
      break
    }
    catch (_: TimeoutException) {
    }
  }
}
