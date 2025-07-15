// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events

import com.intellij.history.LocalHistory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbServiceImpl.Companion.isSynchronousTaskExecution
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexImpl
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.SystemProperties
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.CoroutineDispatcherBackedExecutor
import com.intellij.util.concurrency.createBoundedTaskExecutor
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.FileBasedIndexProjectHandler
import com.intellij.util.indexing.IndexUpToDateCheckIn.isUpToDateCheckEnabled
import com.intellij.util.indexing.IndexingStamp
import com.intellij.util.indexing.events.VfsEventsMerger.VfsEventProcessor
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

private val LOG = Logger.getInstance(ChangedFilesCollector::class.java)

internal class ChangedFilesCollector internal constructor(coroutineScope: CoroutineScope) : IndexedFilesListener() {
  val dirtyFiles: DirtyFiles = DirtyFiles()

  private val myProcessedEventIndex = AtomicInteger()
  private val myWorkersFinishedSync: Phaser = object : Phaser() {
    override fun onAdvance(phase: Int, registeredParties: Int): Boolean {
      return false
    }
  }

  private val vfsEventsExecutor: CoroutineDispatcherBackedExecutor
  private val myScheduledVfsEventsWorkers = AtomicInteger()
  private val myFileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl

  init {
    vfsEventsExecutor = createBoundedTaskExecutor("FileBasedIndex Vfs Event Processor", coroutineScope)
  }

  override fun iterateIndexableFiles(file: VirtualFile, iterator: ContentIterator) {
    if (myFileBasedIndex.belongsToIndexableFiles(file)) {
      VfsUtilCore.visitChildrenRecursively(file, object : VirtualFileVisitor<Void?>() {
        override fun visitFile(file11: VirtualFile): Boolean {
          if (!myFileBasedIndex.belongsToIndexableFiles(file11)) return false
          iterator.processFile(file11)
          return true
        }
      })
    }
  }

  fun clear() {
    dirtyFiles.clear()
    ReadAction.run<RuntimeException?>(ThrowableRunnable {
      if (ApplicationManager.getApplication() == null) {
        // If the application is already disposed (ApplicationManager.getApplication() == null)
        // it means that this method is invoked via ShutDownTracker and the process will be shut down
        // so we don't need to clear collectors.
        return@ThrowableRunnable
      }
      processFilesInReadAction(VfsEventProcessor { true })
    })
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
    if (fileOrDir !is VirtualFileWithId) return
    val id = fileOrDir.getId()
    val projects = myFileBasedIndex.getIndexableFilesFilterHolder().findProjectsForFile(FileBasedIndex.getFileId(fileOrDir))
    dirtyFiles.addFile(projects, id)
  }

  override fun prepareChange(events: MutableList<out VFileEvent>): AsyncFileListener.ChangeApplier {
    val shouldCleanup = events.any({ event: VFileEvent -> memoryStorageCleaningNeeded(event) })
    val superApplier = super.prepareChange(events)

    return object : AsyncFileListener.ChangeApplier {
      override fun beforeVfsChange() {
        if (shouldCleanup) {
          myFileBasedIndex.cleanupMemoryStorage(false)
        }
        superApplier.beforeVfsChange()
      }

      override fun afterVfsChange() {
        superApplier.afterVfsChange()
        val registeredIndexes = myFileBasedIndex.getRegisteredIndexes()
        if (registeredIndexes != null && registeredIndexes.isInitialized()) ensureUpToDateAsync()
      }
    }
  }

  fun ensureUpToDate() {
    if (!isUpToDateCheckEnabled()) {
      return
    }
    //assert ApplicationManager.getApplication().isReadAccessAllowed() || ShutDownTracker.isShutdownHookRunning();
    myFileBasedIndex.waitUntilIndicesAreInitialized()

    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      processFilesToUpdateInReadAction()
    }
    else {
      processFilesInReadActionWithYieldingToWriteAction()
    }
  }

  fun ensureUpToDateAsync() {
    if (getEventMerger().getApproximateChangesCount() < 20 || !myScheduledVfsEventsWorkers.compareAndSet(0, 1)) {
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
        if (`is`("try.starting.dumb.mode.where.many.files.changed")) {
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
        myScheduledVfsEventsWorkers.decrementAndGet()
      }
    })
  }

  fun processFilesToUpdateInReadAction() {
    processFilesInReadAction(object : VfsEventProcessor {
      private val perFileElementTypeUpdateProcessor = (StubIndex.getInstance() as StubIndexImpl).getPerFileElementTypeModificationTrackerUpdateProcessor()
      override fun process(info: VfsEventsMerger.ChangeInfo): Boolean {
        LOG.debug("Processing ", info)
        try {
          val fileId = info.getFileId()
          val file = info.getFile()
          val dirtyQueueProjects = dirtyFiles.getProjects(info.getFileId())
          if (info.isTransientStateChanged()) myFileBasedIndex.doTransientStateChangeForFile(fileId, file, dirtyQueueProjects)
          if (info.isContentChanged()) myFileBasedIndex.scheduleFileForIndexing(fileId, file, true, dirtyQueueProjects)
          if (info.isFileRemoved()) myFileBasedIndex.doInvalidateIndicesForFile(fileId, file, mutableSetOf<Project?>(), dirtyQueueProjects)
          if (info.isFileAdded()) myFileBasedIndex.scheduleFileForIndexing(fileId, file, false, dirtyQueueProjects)
          if (StubIndexImpl.PER_FILE_ELEMENT_TYPE_STUB_CHANGE_TRACKING_SOURCE ==
            StubIndexImpl.PerFileElementTypeStubChangeTrackingSource.ChangedFilesCollector
          ) {
            perFileElementTypeUpdateProcessor.processUpdate(file)
          }
        }
        catch (t: Throwable) {
          if (LOG.isDebugEnabled()) LOG.debug("Exception while processing " + info, t)
          throw t
        }
        finally {
          dirtyFiles.removeFile(info.getFileId())
        }
        return true
      }

      override fun endBatch() {
        if (StubIndexImpl.PER_FILE_ELEMENT_TYPE_STUB_CHANGE_TRACKING_SOURCE ==
          StubIndexImpl.PerFileElementTypeStubChangeTrackingSource.ChangedFilesCollector
        ) {
          perFileElementTypeUpdateProcessor.endUpdatesBatch()
        }
      }
    })
  }

  private fun processFilesInReadAction(processor: VfsEventProcessor) {
    ApplicationManager.getApplication().assertReadAccessAllowed()

    val publishedEventIndex = getEventMerger().getPublishedEventIndex()
    val processedEventIndex = myProcessedEventIndex.get()
    if (processedEventIndex == publishedEventIndex) {
      return
    }

    myWorkersFinishedSync.register()
    val phase = myWorkersFinishedSync.getPhase()
    try {
      myFileBasedIndex.waitUntilIndicesAreInitialized()
      getEventMerger().processChanges(object : VfsEventProcessor {
        override fun process(changeInfo: VfsEventsMerger.ChangeInfo): Boolean {
          return ConcurrencyUtil.withLock(myFileBasedIndex.myWriteLock, ThrowableComputable {
            try {
              ProgressManager.getInstance().executeNonCancelableSection(Runnable {
                processor.process(changeInfo)
              })
            }
            finally {
              IndexingStamp.flushCache(changeInfo.getFileId())
            }
            true
          })
        }

        override fun endBatch() {
          ConcurrencyUtil.withLock<RuntimeException?>(myFileBasedIndex.myWriteLock, ThrowableRunnable {
            processor.endBatch()
          })
        }
      })
    }
    finally {
      myWorkersFinishedSync.arriveAndDeregister()
    }

    try {
      awaitWithCheckCancelled(myWorkersFinishedSync, phase)
    }
    catch (e: RejectedExecutionException) {
      LOG.warn(e)
      throw ProcessCanceledException(e)
    }
    catch (e: InterruptedException) {
      LOG.warn(e)
      throw ProcessCanceledException(e)
    }

    if (getEventMerger().getPublishedEventIndex() == publishedEventIndex) {
      myProcessedEventIndex.compareAndSet(processedEventIndex, publishedEventIndex)
    }
  }

  private fun processFilesInReadActionWithYieldingToWriteAction() {
    while (getEventMerger().hasChanges()) {
      ReadAction.nonBlocking<Void?>(Callable {
        processFilesToUpdateInReadAction()
        null
      } as Callable<Void?>).executeSynchronously()
    }
  }

  @TestOnly
  fun waitForVfsEventsExecuted(timeout: Long, unit: TimeUnit) {
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
      catch (e: TimeoutCancellationException) {
        UIUtil.dispatchAllInvocationEvents()
      }
    }
  }

  companion object {
    val CLEAR_NON_INDEXABLE_FILE_DATA: Boolean = SystemProperties.getBooleanProperty("idea.indexes.clear.non.indexable.file.data", true)
  }
}

private fun memoryStorageCleaningNeeded(event: VFileEvent): Boolean {
  val requestor = event.getRequestor()
  return requestor is FileDocumentManager ||
         requestor is PsiManager || requestor === LocalHistory.VFS_EVENT_REQUESTOR
}

@Throws(InterruptedException::class)
private fun awaitWithCheckCancelled(phaser: Phaser, phase: Int) {
  while (true) {
    ProgressManager.checkCanceled()
    try {
      phaser.awaitAdvanceInterruptibly(phase, 100, TimeUnit.MILLISECONDS)
      break
    }
    catch (ignored: TimeoutException) {
    }
  }
}
