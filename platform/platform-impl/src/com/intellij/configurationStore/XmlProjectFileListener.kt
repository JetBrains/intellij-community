// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class XmlProjectFileListener(
  private val fileNameSuffix: String,
  private val requestorClass: Class<*>,
) : AsyncFileListener {
  private fun isConfigurationFile(path: String) = !path.contains("/.idea/") && PathUtil.getFileName(path).endsWith(fileNameSuffix)

  protected abstract fun updateFiles(project: Project, deletedFilePaths: Collection<String>, updatedFilePaths: Collection<String>)

  override fun prepareChange(events: List<VFileEvent>): @org.jetbrains.annotations.Nullable AsyncFileListener.ChangeApplier? {
    val deletedFilePaths = mutableSetOf<String>()
    val updatedFilePaths = mutableSetOf<String>()

    for (event in events) {
      if (!event.fileSystem.isLocal || requestorClass.isInstance(event.requestor)) {
        continue
      }

      if (event is VFileContentChangeEvent || event is VFileCreateEvent) {
        if (isConfigurationFile(event.path)) {
          updatedFilePaths.add(event.path)
          deletedFilePaths.remove(event.path)
        }
      }
      else if (event is VFileCopyEvent) {
        if (isConfigurationFile(event.newParent.path + "/" + event.newChildName)) {
          updatedFilePaths.add(event.path)
          deletedFilePaths.remove(event.path)
        }
      }
      else if (event is VFileDeleteEvent) {
        if (isConfigurationFile(event.path)) {
          updatedFilePaths.remove(event.path)
          deletedFilePaths.add(event.path)
        }
      }
      else if (event is VFileMoveEvent) {
        if (isConfigurationFile(event.oldPath)) {
          updatedFilePaths.remove(event.oldPath)
          deletedFilePaths.add(event.oldPath)
        }
        if (isConfigurationFile(event.newPath)) {
          updatedFilePaths.add(event.newPath)
          deletedFilePaths.remove(event.newPath)
        }
      }
      else if (event is VFilePropertyChangeEvent && event.isRename) {
        if (isConfigurationFile(event.oldPath)) {
          updatedFilePaths.remove(event.oldPath)
          deletedFilePaths.add(event.oldPath)
        }
        if (isConfigurationFile(event.newPath)) {
          updatedFilePaths.add(event.newPath)
          deletedFilePaths.remove(event.newPath)
        }
      }

      if (updatedFilePaths.isNotEmpty() || deletedFilePaths.isNotEmpty()) {
        return object : AsyncFileListener.ChangeApplier {
          override fun afterVfsChange() {
            for (project in getOpenedProjects()) {
              updateFiles(project, deletedFilePaths, updatedFilePaths)
            }
          }
        }
      }
    }

    return null
  }
}
