// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileTypeEvent
import com.intellij.openapi.fileTypes.FileTypeListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.UnindexedFilesScannerExecutor
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.psi.stubs.StubIndexExtension
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.indexing.IndexingFlag.cleanupProcessedFlag
import com.intellij.util.indexing.InitialScanningSkipReporter.SourceOfScanning
import com.intellij.util.indexing.PersistentDirtyFilesQueue.getQueueFile
import com.intellij.util.indexing.PersistentDirtyFilesQueue.readProjectDirtyFilesQueue
import com.intellij.util.indexing.diagnostic.ScanningType.FULL_ON_INDEX_RESTART
import com.intellij.util.indexing.diagnostic.ScanningType.PARTIAL_ON_INDEX_RESTART
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.util.*

class FileBasedIndexTumbler(private val reason: @NonNls String) {
  private val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
  private val dumbModeSemaphore = Semaphore()

  private var nestedLevelCount = 0
  private var snapshot: FbiSnapshot? = null
  private var fileTypeTracker: FileTypeTracker? = null
  private var allowSkippingFullScanning: Boolean = false

  @ApiStatus.Internal
  @TestOnly
  fun allowSkippingFullScanning() {
    allowSkippingFullScanning = true
  }

  fun turnOff() {
    LOG.info("Turning off file-based index. Reason: `$reason`. Nested level count: before=$nestedLevelCount, after=${nestedLevelCount + 1}")
    val app = ApplicationManager.getApplication()
    ThreadingAssertions.assertEventDispatchThread()
    LOG.assertTrue(!app.isWriteAccessAllowed)
    try {
      if (nestedLevelCount == 0) {
          val wasUp = dumbModeSemaphore.isUp
          dumbModeSemaphore.down()
          if (wasUp) {
            for (project in ProjectUtil.getOpenProjects()) {
              val scannerExecutor = UnindexedFilesScannerExecutor.getInstance(project)
              scannerExecutor.suspendQueue()
              scannerExecutor.cancelAllTasksAndWait()

              val perProjectIndexingQueue = project.getService(PerProjectIndexingQueue::class.java)
              perProjectIndexingQueue.clear()

              val dumbService = DumbService.getInstance(project)
              dumbService.cancelAllTasksAndWait()
              MyDumbModeTask(dumbModeSemaphore).queue(project)
            }
          }

        LOG.assertTrue(fileTypeTracker == null)
        fileTypeTracker = FileTypeTracker()
        fileBasedIndex.waitUntilIndicesAreInitialized()
        fileBasedIndex.performShutdown(true, reason)
        fileBasedIndex.dropRegisteredIndexes()
        val indexesAreOk = RebuildStatus.isOk()
        RebuildStatus.reset()
        IndexingStamp.dropTimestampMemoryCaches()
        LOG.assertTrue(snapshot == null)
        if (indexesAreOk) {
          snapshot = FbiSnapshot.Impl.capture()
        }
        else {
          snapshot = FbiSnapshot.RebuildRequired
        }
      }
    }
    finally {
      nestedLevelCount++
    }
  }

  @JvmOverloads
  fun turnOn(beforeIndexTasksStarted: Runnable? = null) {
    LOG.info("Turning on file-based index. Reason: `$reason`. Nested level count: before=$nestedLevelCount, after=${nestedLevelCount - 1}")
    LOG.assertTrue(ApplicationManager.getApplication().isWriteIntentLockAcquired)
    nestedLevelCount--
    LOG.assertTrue(nestedLevelCount >= 0, "nestedLevelCount is less than 0: $nestedLevelCount. " +
                                          "This probably means that DynamicPluginListener.plugin[Un]Loaded event was fired without corresponding 'before' event")
    if (nestedLevelCount == 0) {
      try {
        fileBasedIndex.loadIndexes()
        if (DumbServiceImpl.isSynchronousTaskExecution) {
          fileBasedIndex.waitUntilIndicesAreInitialized()
        }
        for (project in ProjectUtil.getOpenProjects()) {
          UnindexedFilesScannerExecutor.getInstance(project).resumeQueue()
          FileBasedIndexInfrastructureExtension.attachAllExtensionsData(project)
        }
        dumbModeSemaphore.up()

        val runRescanning = CorruptionMarker.requireInvalidation() || (Registry.`is`("run.index.rescanning.on.plugin.load.unload") ||
                            snapshot is FbiSnapshot.RebuildRequired ||
                            FbiSnapshot.Impl.isRescanningRequired(snapshot as FbiSnapshot.Impl, FbiSnapshot.Impl.capture()))
        if (runRescanning) {
          val registeredIndexes = fileBasedIndex.registeredIndexes
          beforeIndexTasksStarted?.run()
          if (!allowSkippingFullScanning) {
            cleanupProcessedFlag(reason)
          }
          for (project in ProjectUtil.getOpenProjects()) {
            val projectQueueFile = project.getQueueFile()
            val projectDirtyFilesQueue = readProjectDirtyFilesQueue(projectQueueFile, ManagingFS.getInstance().creationTimestamp)
            fileBasedIndex.dirtyFiles.getProjectDirtyFiles(project)?.addFiles(projectDirtyFilesQueue.fileIds)
            fileBasedIndex.setLastSeenIndexInOrphanQueue(project, projectDirtyFilesQueue.lastSeenIndexInOrphanQueue)
            val indexesWereCorrupted = registeredIndexes.wasCorrupted
            val indexesCleanupJob = scanAndIndexProjectAfterOpen(
              project = project,
              orphanQueue = registeredIndexes.orphanDirtyFilesQueue,
              orphanQueueDiscardReason = registeredIndexes.orphanDirtyFilesQueueDiscardReason,
              additionalOrphanDirtyFiles = emptySet(),
              projectDirtyFilesQueue = projectDirtyFilesQueue,

              allowSkippingFullScanning = allowSkippingFullScanning && !indexesWereCorrupted,
              requireReadingIndexableFilesIndexFromDisk = !allowSkippingFullScanning,
              coroutineScope = (project as ComponentManagerEx).getCoroutineScope(),
              indexingReason = "On FileBasedIndexTumbler.turnOn (reason=$reason)",
              fullScanningType = FULL_ON_INDEX_RESTART,
              partialScanningType = PARTIAL_ON_INDEX_RESTART,
              registeredIndexesWereCorrupted = indexesWereCorrupted,
              sourceOfScanning = SourceOfScanning.IndexTumblerOn,
            )
            indexesCleanupJob.forgetProjectDirtyFilesOnCompletion(fileBasedIndex, project, projectDirtyFilesQueue, registeredIndexes.orphanDirtyFilesQueue.untrimmedSize)
          }
          LOG.info("Index rescanning has been started. Reason: `$reason`")
        }
        else {
          LOG.info("Index rescanning has been skipped. Reason `$reason`")
        }
      }
      finally {
        fileTypeTracker?.let { Disposer.dispose(it) }
        fileTypeTracker = null
        snapshot = null
      }
    }
  }

  companion object {
    private val LOG = logger<FileBasedIndexTumbler>()

    private class MyDumbModeTask(val semaphore: Semaphore) : DumbModeTask() {
      override fun performInDumbMode(indicator: ProgressIndicator) {
        if (DumbServiceImpl.isSynchronousTaskExecution) {
          return // TODO: this will be a deadlock otherwise (IJPL-578)
        }
        indicator.text = IndexingBundle.message("indexes.reloading")
        semaphore.waitFor()
      }

      override fun toString(): String {
        return "Plugin loading/unloading"
      }

      override fun tryMergeWith(taskFromQueue: DumbModeTask): DumbModeTask? =
        if (taskFromQueue is MyDumbModeTask && taskFromQueue.semaphore === semaphore) this else null
    }
  }
}

internal sealed class FbiSnapshot {
  internal object RebuildRequired : FbiSnapshot()

  internal data class Impl(private val ideIndexVersion: MyIdeIndexVersion) : FbiSnapshot() {
    companion object {
      fun capture(): Impl {
        val additionalLibraryRootsProvider: SortedSet<String> = AdditionalLibraryRootsProvider.EP_NAME.extensionList.map { it.toString() }.toSortedSet()
        val indexableSetContributor: SortedSet<String> = IndexableSetContributor.EP_NAME.extensionList.map { it.debugName }.toSortedSet()

        val baseIndexes: Map<String, String> = emptyMap()
        val fileBasedIndexVersions =
          IndexInfrastructureVersionBase.fileBasedIndexVersions(FileBasedIndexExtension.EXTENSION_POINT_NAME.extensionList) { it.version.toString() }
        val stubIndexVersions = IndexInfrastructureVersionBase.stubIndexVersions(StubIndexExtension.EP_NAME.extensionList)
        val stubFileElementTypeVersions = IndexInfrastructureVersionBase.stubFileElementTypeVersions()
        val compositeBinaryStubFileBuilderVersions = IndexInfrastructureVersionBase.getAllCompositeBinaryFileStubBuilderVersions()

        return Impl(MyIdeIndexVersion(additionalLibraryRootsProvider,
                                      indexableSetContributor,
                                      baseIndexes,
                                      fileBasedIndexVersions,
                                      stubIndexVersions,
                                      stubFileElementTypeVersions,
                                      compositeBinaryStubFileBuilderVersions))
      }

      fun isRescanningRequired(oldFbiSnapshot: Impl, newFbiSnapshot: Impl): Boolean {
        val oldVersion = oldFbiSnapshot.ideIndexVersion
        val newVersion = newFbiSnapshot.ideIndexVersion

        if (oldVersion.myBaseIndexes != newVersion.myBaseIndexes) {
          return true
        }

        if (oldVersion.myStubFileElementTypeVersions != newVersion.myStubFileElementTypeVersions) {
          return true
        }

        if (oldVersion.myCompositeBinaryStubFileBuilderVersions != newVersion.myCompositeBinaryStubFileBuilderVersions) {
          return true
        }

        if (oldVersion.myStubIndexVersions != newVersion.myStubIndexVersions) {
          return true
        }

        if (oldVersion.myFileBasedIndexVersions.entries.containsAll(newVersion.myFileBasedIndexVersions.entries) &&
            oldVersion.additionalLibraryRootsProvider.containsAll(newVersion.additionalLibraryRootsProvider) &&
            oldVersion.indexableSetContributor.containsAll(newVersion.indexableSetContributor)) {
          return false
        }

        return true
      }
    }
  }
}

internal class MyIdeIndexVersion(val additionalLibraryRootsProvider: SortedSet<String>,
                                 val indexableSetContributor: SortedSet<String>,
                                 baseIndexes: Map<String, String>,
                                 fileBasedIndexVersions: Map<String, FileBasedIndexVersionInfo>,
                                 stubIndexVersions: Map<String, String>,
                                 stubFileElementTypeVersions: Map<String, String>,
                                 compositeBinaryStubFileBuilderVersions: Map<String, String>) : IndexInfrastructureVersionBase(
  baseIndexes,
  fileBasedIndexVersions,
  stubIndexVersions,
  stubFileElementTypeVersions,
  compositeBinaryStubFileBuilderVersions) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as MyIdeIndexVersion

    if (additionalLibraryRootsProvider != other.additionalLibraryRootsProvider) return false
    if (indexableSetContributor != other.indexableSetContributor) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + additionalLibraryRootsProvider.hashCode()
    result = 31 * result + indexableSetContributor.hashCode()
    return result
  }
}

private class FileTypeTracker: Disposable {
  var changed: Boolean = false

  init {
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(FileTypeManager.TOPIC, object : FileTypeListener {
      override fun beforeFileTypesChanged(event: FileTypeEvent) {
        changed = true
      }
    })
  }

  override fun dispose() = Unit
}