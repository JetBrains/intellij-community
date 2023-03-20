// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.openapi.project.processOpenedProjects
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.events.*

private class RCInArbitraryFileListener : AsyncFileListener {
  override fun prepareChange(events: List<VFileEvent>): @org.jetbrains.annotations.Nullable com.intellij.openapi.vfs.AsyncFileListener.ChangeApplier? {
    val deletedRCFilePaths = mutableSetOf<String>()
    val updatedRCFilePaths = mutableSetOf<String>()

    for (event in events) {
      if (event.fileSystem !is LocalFileSystem || event.requestor is RCInArbitraryFileManager) {
        continue
      }

      if (event is VFileContentChangeEvent || event is VFileCreateEvent) {
        if (RunConfigurationInArbitraryFileScanner.isFileWithRunConfigs(event.path)) {
          updatedRCFilePaths.add(event.path)
          deletedRCFilePaths.remove(event.path)
        }
      }
      else if (event is VFileCopyEvent) {
        if (RunConfigurationInArbitraryFileScanner.isFileWithRunConfigs(event.newParent.path + "/" + event.newChildName)) {
          updatedRCFilePaths.add(event.path)
          deletedRCFilePaths.remove(event.path)
        }
      }
      else if (event is VFileDeleteEvent) {
        if (RunConfigurationInArbitraryFileScanner.isFileWithRunConfigs(event.path)) {
          updatedRCFilePaths.remove(event.path)
          deletedRCFilePaths.add(event.path)
        }
      }
      else if (event is VFileMoveEvent) {
        if (RunConfigurationInArbitraryFileScanner.isFileWithRunConfigs(event.oldPath)) {
          updatedRCFilePaths.remove(event.oldPath)
          deletedRCFilePaths.add(event.oldPath)
        }
        if (RunConfigurationInArbitraryFileScanner.isFileWithRunConfigs(event.newPath)) {
          updatedRCFilePaths.add(event.newPath)
          deletedRCFilePaths.remove(event.newPath)
        }
      }
      else if (event is VFilePropertyChangeEvent && event.isRename) {
        if (RunConfigurationInArbitraryFileScanner.isFileWithRunConfigs(event.oldPath)) {
          updatedRCFilePaths.remove(event.oldPath)
          deletedRCFilePaths.add(event.oldPath)
        }
        if (RunConfigurationInArbitraryFileScanner.isFileWithRunConfigs(event.newPath)) {
          updatedRCFilePaths.add(event.newPath)
          deletedRCFilePaths.remove(event.newPath)
        }
      }

      if (updatedRCFilePaths.isNotEmpty() || deletedRCFilePaths.isNotEmpty()) {
        return object : AsyncFileListener.ChangeApplier {
          override fun afterVfsChange() {
            processOpenedProjects {
              RunManagerImpl.getInstanceImpl(it).updateRunConfigsFromArbitraryFiles(deletedRCFilePaths, updatedRCFilePaths)
            }
          }
        }
      }
    }

    return null
  }
}