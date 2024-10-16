// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl
import com.intellij.openapi.startup.StartupActivity.RequiredForSmartMode
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.util.ThrowableRunnable
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.indexing.PersistentDirtyFilesQueue.getQueueFile
import com.intellij.util.indexing.PersistentDirtyFilesQueue.readProjectDirtyFilesQueue
import com.intellij.util.indexing.diagnostic.ScanningType
import kotlinx.coroutines.CoroutineScope

internal class ProjectFileBasedIndexStartupActivity(private val coroutineScope: CoroutineScope) : RequiredForSmartMode {
  private val openProjects = ContainerUtil.createConcurrentList<Project?>()

  init {
    ApplicationManager.getApplication().getMessageBus().simpleConnect().subscribe<ProjectCloseListener>(ProjectCloseListener.TOPIC,
                                                                                                        object : ProjectCloseListener {
                                                                                                          override fun projectClosing(
                                                                                                            project: Project
                                                                                                          ) {
                                                                                                            onProjectClosing(project)
                                                                                                          }
                                                                                                        })
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
    val registeredIndexes = fileBasedIndex.getRegisteredIndexes()
    if (registeredIndexes == null) return
    val wasCorrupted = registeredIndexes.getWasCorrupted()

    val projectQueueFile = project.getQueueFile()
    val vfsCreationTimestamp = ManagingFS.getInstance().getCreationTimestamp()
    val projectDirtyFilesQueue = readProjectDirtyFilesQueue(projectQueueFile, vfsCreationTimestamp)

    // Add project to various lists in read action to make sure that
    // they are not added to lists during disposing of project (in this case project may be stuck forever in those lists)
    val registered = ReadAction.compute<Boolean?, RuntimeException?>(ThrowableComputable {
      if (project.isDisposed()) {
        return@ThrowableComputable false
      }
      // done mostly for tests. In real life this is no-op, because the set was removed on project closing
      // note that disposing happens in write action, so it'll be executed after this read action
      Disposer.register(project, Disposable { onProjectClosing(project) })

      fileBasedIndex.registerProject(project, projectDirtyFilesQueue.fileIds)
      fileBasedIndex.registerProjectFileSets(project)
      fileBasedIndex.setLastSeenIndexInOrphanQueue(project, projectDirtyFilesQueue.lastSeenIndexInOrphanQueue)
      fileBasedIndex.getIndexableFilesFilterHolder().onProjectOpened(project, vfsCreationTimestamp)

      openProjects.add(project)
      true
    })

    if (!registered) return

    // schedule dumb mode start after the read action we're currently in
    val orphanQueue = registeredIndexes.getOrphanDirtyFilesQueue()
    val indexesCleanupJob = scanAndIndexProjectAfterOpen(project,
                                                         orphanQueue,
                                                         fileBasedIndex.getAllDirtyFiles(null),
                                                         projectDirtyFilesQueue,
                                                         !wasCorrupted,
                                                         true,
                                                         coroutineScope,
                                                         "On project open",
                                                         ScanningType.FULL_ON_PROJECT_OPEN,
                                                         ScanningType.PARTIAL_ON_PROJECT_OPEN,
                                                         wasCorrupted,
                                                         InitialScanningSkipReporter.SourceOfScanning.OnProjectOpen)
    indexesCleanupJob.forgetProjectDirtyFilesOnCompletion(fileBasedIndex, project, projectDirtyFilesQueue, orphanQueue.untrimmedSize)
  }

  private fun onProjectClosing(project: Project) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(Runnable {
      ReadAction.run<RuntimeException?>(ThrowableRunnable {
        if (openProjects.remove(project)) {
          FileBasedIndex.getInstance().onProjectClosing(project)
        }
      })
    }, IndexingBundle.message("removing.indexable.set.project.handler"), false, project)
  }
}
