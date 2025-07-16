// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Lock
import kotlin.concurrent.withLock

private val LOG = logger<ChangedFilesCollector>()

@ApiStatus.Internal
class ChangedFilesCollector internal constructor(coroutineScope: CoroutineScope) : IndexedFilesListener() {
  val dirtyFiles: DirtyFiles = DirtyFiles()

  private val processedEventIndex = AtomicInteger()

  private val workersFinishedSync: Phaser = object : Phaser() {
    override fun onAdvance(phase: Int, registeredParties: Int): Boolean = false
  }

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
      processFilesInReadAction(VfsEventProcessor { true })
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

    val id = fileOrDir.getId()
    val projects = fileBasedIndex.indexableFilesFilterHolder.findProjectsForFile(FileBasedIndex.getFileId(fileOrDir))
    dirtyFiles.addFile(projects, id)
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
    if (eventMerger.approximateChangesCount < 20 || !scheduledVfsEventsWorkers.compareAndSet(0, 1)) {
      return
    }

    if (isSynchronousTaskExecution) {
      ensureUpToDate()
      return
    }

    vfsEventsExecutor.execute(Runnable {
      try {
        processFilesInReadActionWithYieldingToWriteAction()

        @Suppress("IncorrectCancellationExceptionHandling")
        if (Registry.`is`("try.starting.dumb.mode.where.many.files.changed")) {
          for (project in ProjectManager.getInstance().getOpenProjects()) {
            try {
              FileBasedIndexProjectHandler.scheduleReindexingInDumbMode(project)
            }
            catch (_: ProcessCanceledException) {
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
    })
  }

  fun processFilesToUpdateInReadAction() {
    processFilesInReadAction(object : VfsEventProcessor {
      private val perFileElementTypeUpdateProcessor = (StubIndex.getInstance() as StubIndexImpl)
        .perFileElementTypeModificationTrackerUpdateProcessor

      override fun process(info: VfsEventsMerger.ChangeInfo): Boolean {
        LOG.debug("Processing ", info)
        try {
          val fileId = info.getFileId()
          val file = info.file
          val dirtyQueueProjects = dirtyFiles.getProjects(info.getFileId())
          if (info.isTransientStateChanged) {
            fileBasedIndex.doTransientStateChangeForFile(fileId, file, dirtyQueueProjects)
          }
          if (info.isContentChanged) {
            fileBasedIndex.scheduleFileForIndexing(fileId, file, true, dirtyQueueProjects)
          }
          if (info.isFileRemoved) {
            fileBasedIndex.doInvalidateIndicesForFile(fileId, file, emptySet(), dirtyQueueProjects)
          }
          if (info.isFileAdded) {
            fileBasedIndex.scheduleFileForIndexing(fileId, file, false, dirtyQueueProjects)
          }
          if (StubIndexImpl.PER_FILE_ELEMENT_TYPE_STUB_CHANGE_TRACKING_SOURCE ==
            StubIndexImpl.PerFileElementTypeStubChangeTrackingSource.ChangedFilesCollector) {
            perFileElementTypeUpdateProcessor.processUpdate(file)
          }
        }
        catch (t: Throwable) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Exception while processing $info", t)
          }
          throw t
        }
        finally {
          dirtyFiles.removeFile(info.getFileId())
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
              ProgressManager.getInstance().executeNonCancelableSection(Runnable {
                processor.process(changeInfo)
              })
            }
            finally {
              IndexingStamp.flushCache(changeInfo.getFileId())
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
      phaser.awaitAdvanceInterruptibly(phase, 100, TimeUnit.MILLISECONDS)
      break
    }
    catch (_: TimeoutException) {
    }
  }
}
