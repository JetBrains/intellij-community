// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl
import com.intellij.openapi.startup.StartupActivity.RequiredForSmartMode
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.indexing.PersistentDirtyFilesQueue.getQueueFile
import com.intellij.util.indexing.PersistentDirtyFilesQueue.readProjectDirtyFilesQueue
import com.intellij.util.indexing.diagnostic.ScanningType
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
private class ProjectFileBasedIndexStartupActivityScope(@JvmField val coroutineScope: CoroutineScope)

private class ProjectFileBasedIndexStartupActivity : RequiredForSmartMode {
  private val openProjects = ContainerUtil.createConcurrentList<Project?>()

  init {
    ApplicationManager.getApplication().getMessageBus().simpleConnect().subscribe<ProjectCloseListener>(
      ProjectCloseListener.TOPIC,
      object : ProjectCloseListener {
        override fun projectClosing(project: Project) {
          onProjectClosing(project)
        }
      },
    )
  }

  override fun runActivity(project: Project) {
    ProgressManager.progress(IndexingBundle.message("progress.text.loading.indexes"))
    val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
    val propertiesUpdater = PushedFilePropertiesUpdater.getInstance(project)
    if (propertiesUpdater is PushedFilePropertiesUpdaterImpl) {
      propertiesUpdater.initializeProperties()
    }

    // load indexes while in dumb mode, otherwise someone from read action may hit `FileBasedIndex.getIndex` and hang (IDEA-316697)
    fileBasedIndex.loadIndexes()
    val registeredIndexes = fileBasedIndex.registeredIndexes ?: return

    val wasCorrupted = registeredIndexes.wasCorrupted

    val projectQueueFile = project.getQueueFile()
    val vfsCreationTimestamp = ManagingFS.getInstance().getCreationTimestamp()
    val projectDirtyFilesQueue = readProjectDirtyFilesQueue(projectQueueFile, vfsCreationTimestamp)

    // Add a project to various lists in read action to make sure that
    // they are not added to lists during disposing of a project (in this case project may be stuck forever in those lists)
    val registered = ApplicationManager.getApplication().runReadAction(ThrowableComputable {
      if (project.isDisposed()) {
        return@ThrowableComputable false
      }

      // Done mostly for tests.
      // In real life this is no-op, because the set was removed on project closing
      // note that disposing happens in write action, so it'll be executed after this read action
      Disposer.register(project) { onProjectClosing(project) }

      fileBasedIndex.registerProject(project, projectDirtyFilesQueue.fileIds)
      fileBasedIndex.registerProjectFileSets(project)
      fileBasedIndex.setLastSeenIndexInOrphanQueue(project, projectDirtyFilesQueue.lastSeenIndexInOrphanQueue)
      fileBasedIndex.indexableFilesFilterHolder.onProjectOpened(project, vfsCreationTimestamp)

      openProjects.add(project)
      true
    })

    if (!registered) {
      return
    }

    // schedule dumb mode start after the read action we're currently in
    val orphanQueue = registeredIndexes.orphanDirtyFilesQueue
    val indexCleanupJob = scanAndIndexProjectAfterOpen(
      project = project,
      orphanQueue = orphanQueue,
      additionalOrphanDirtyFiles = fileBasedIndex.getAllDirtyFiles(null),
      projectDirtyFilesQueue = projectDirtyFilesQueue,
      allowSkippingFullScanning = !wasCorrupted,
      requireReadingIndexableFilesIndexFromDisk = true,
      coroutineScope = project.service<ProjectFileBasedIndexStartupActivityScope>().coroutineScope,
      indexingReason = "On project open",
      fullScanningType = ScanningType.FULL_ON_PROJECT_OPEN,
      partialScanningType = ScanningType.PARTIAL_ON_PROJECT_OPEN,
      registeredIndexesWereCorrupted = wasCorrupted,
      sourceOfScanning = InitialScanningSkipReporter.SourceOfScanning.OnProjectOpen,
    )
    indexCleanupJob.forgetProjectDirtyFilesOnCompletion(
      fileBasedIndex = fileBasedIndex,
      project = project,
      projectDirtyFilesQueue = projectDirtyFilesQueue,
      orphanQueueUntrimmedSize = orphanQueue.untrimmedSize,
    )
  }

  private fun onProjectClosing(project: Project) {
    runWithModalProgressBlocking(
      owner = ModalTaskOwner.project(project),
      title = IndexingBundle.message("removing.indexable.set.project.handler"),
      cancellation = TaskCancellation.nonCancellable(),
    ) {
      if (openProjects.remove(project)) {
        val fileBasedIndex = serviceIfCreated<FileBasedIndex>() ?: return@runWithModalProgressBlocking
        readAction {
          fileBasedIndex.onProjectClosing(project)
        }
      }
    }
  }
}
