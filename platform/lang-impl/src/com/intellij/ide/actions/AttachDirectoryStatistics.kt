// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal object AttachDirectoryUsagesCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup(
    id = "attach.directory.statistics",
    version = 1,
    description = "Reports file count in attached directories"
  )

  private val ATTACHED_DIRECTORY_EVENT = GROUP.registerEvent(
    eventId = "attached.directory",
    eventField1 = EventFields.LogarithmicInt("files_count"),
    description = "Reports the count of files located under an attached root directory"
  )

  override fun getGroup() = GROUP

  @JvmStatic
  fun logAttachedDirectoryFilesCount(filesCount: Int) {
    ATTACHED_DIRECTORY_EVENT.log(filesCount)
  }
}

@Service(Service.Level.PROJECT)
private class FileCountLogger(private val cs: CoroutineScope) {
  fun logFilesOnDiskCount(root: VirtualFile) {
    cs.launch(Dispatchers.IO) {
      val filesCount = countFilesOnDisk(root)
      AttachDirectoryUsagesCollector.logAttachedDirectoryFilesCount(filesCount)
    }
  }

  private fun countFilesOnDisk(root: VirtualFile): Int {
    var count = 0
    VfsUtilCore.iterateChildrenRecursively((root as NewVirtualFile).asCacheAvoiding(), null) { file ->
      if (!file.isDirectory) {
        count++
      }
      count < FILES_COUNT_THRESHOLD
    }
    return count
  }
}

private const val FILES_COUNT_THRESHOLD: Int = 1_000_000

internal fun logFilesOnDiskCount(project: Project, root: VirtualFile) {
  project.service<FileCountLogger>().logFilesOnDiskCount(root)
}
