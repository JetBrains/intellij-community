// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.util.PathUtil

private fun isFileWithRunConfigs(path: String) = !path.contains("/.idea/") && PathUtil.getFileName(path).endsWith(".run.xml")

private class RCInArbitraryFileListener : AsyncFileListener {
  override fun prepareChange(events: List<VFileEvent>): @org.jetbrains.annotations.Nullable AsyncFileListener.ChangeApplier? {
    val deletedRCFilePaths = mutableSetOf<String>()
    val updatedRCFilePaths = mutableSetOf<String>()

    for (event in events) {
      if (event.fileSystem !is LocalFileSystem || event.requestor is RCInArbitraryFileManager) {
        continue
      }

      if (event is VFileContentChangeEvent || event is VFileCreateEvent) {
        if (isFileWithRunConfigs(event.path)) {
          updatedRCFilePaths.add(event.path)
          deletedRCFilePaths.remove(event.path)
        }
      }
      else if (event is VFileCopyEvent) {
        if (isFileWithRunConfigs(event.newParent.path + "/" + event.newChildName)) {
          updatedRCFilePaths.add(event.path)
          deletedRCFilePaths.remove(event.path)
        }
      }
      else if (event is VFileDeleteEvent) {
        if (isFileWithRunConfigs(event.path)) {
          updatedRCFilePaths.remove(event.path)
          deletedRCFilePaths.add(event.path)
        }
      }
      else if (event is VFileMoveEvent) {
        if (isFileWithRunConfigs(event.oldPath)) {
          updatedRCFilePaths.remove(event.oldPath)
          deletedRCFilePaths.add(event.oldPath)
        }
        if (isFileWithRunConfigs(event.newPath)) {
          updatedRCFilePaths.add(event.newPath)
          deletedRCFilePaths.remove(event.newPath)
        }
      }
      else if (event is VFilePropertyChangeEvent && event.isRename) {
        if (isFileWithRunConfigs(event.oldPath)) {
          updatedRCFilePaths.remove(event.oldPath)
          deletedRCFilePaths.add(event.oldPath)
        }
        if (isFileWithRunConfigs(event.newPath)) {
          updatedRCFilePaths.add(event.newPath)
          deletedRCFilePaths.remove(event.newPath)
        }
      }

      if (updatedRCFilePaths.isNotEmpty() || deletedRCFilePaths.isNotEmpty()) {
        return object : AsyncFileListener.ChangeApplier {
          override fun afterVfsChange() {
            for (project in getOpenedProjects()) {
              RunManagerImpl.getInstanceImpl(project).updateRunConfigsFromArbitraryFiles(deletedRCFilePaths, updatedRCFilePaths)
            }
          }
        }
      }
    }

    return null
  }
}