// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore.schemeManager

import com.intellij.configurationStore.LOG
import com.intellij.configurationStore.StoreAwareProjectManager
import com.intellij.configurationStore.StoreReloadManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.SmartList
import com.intellij.util.io.systemIndependentPath

internal class SchemeFileTracker(private val schemeManager: SchemeManagerImpl<Any, Any>, private val project: Project) : BulkFileListener {
  private val applicator = SchemeChangeApplicator(schemeManager)

  override fun after(events: MutableList<out VFileEvent>) {
    val list = SmartList<SchemeChangeEvent>()
    for (event in events) {
      if (event.requestor is SchemeManagerImpl<*, *>) {
        continue
      }

      when (event) {
        is VFileContentChangeEvent -> {
          val file = event.file
          if (isMyFileWithoutParentCheck(file) && isMyDirectory(file.parent)) {
            list.add(UpdateScheme(file))
          }
        }

        is VFileCreateEvent -> {
          if (event.isDirectory) {
            handleDirectoryCreated(event, list)
          }
          else if (schemeManager.canRead(event.childName) && isMyDirectory(event.parent)) {
            event.file?.let {
              list.add(AddScheme(it))
            }
          }
        }

        is VFileDeleteEvent -> {
          val file = event.file
          if (file.isDirectory) {
            handleDirectoryDeleted(file, list)
          }
          else if (isMyFileWithoutParentCheck(file) && isMyDirectory(file.parent)) {
            list.add(RemoveScheme(file.name))
          }
        }
      }
    }

    if (list.isNotEmpty()) {
      (StoreReloadManager.getInstance() as StoreAwareProjectManager).registerChangedSchemes(list, applicator, project)
    }
  }

  private fun isMyFileWithoutParentCheck(file: VirtualFile) = schemeManager.canRead(file.nameSequence)

  private fun isMyDirectory(parent: VirtualFile): Boolean {
    val virtualDirectory = schemeManager.cachedVirtualDirectory
    return when (virtualDirectory) {
      null -> schemeManager.ioDirectory.systemIndependentPath == parent.path
      else -> virtualDirectory == parent
    }
  }

  private fun handleDirectoryDeleted(file: VirtualFile, list: SmartList<SchemeChangeEvent>) {
    if (!StringUtil.equals(file.nameSequence, schemeManager.ioDirectory.fileName.toString())) {
      return
    }

    if (file == schemeManager.virtualDirectory) {
      list.add(RemoveAllSchemes())
    }
  }

  private fun handleDirectoryCreated(event: VFileCreateEvent, list: MutableList<SchemeChangeEvent>) {
    if (event.childName != schemeManager.ioDirectory.fileName.toString()) {
      return
    }

    val dir = schemeManager.virtualDirectory
    if (event.file != dir) {
      return
    }

    for (file in dir!!.children) {
      if (isMyFileWithoutParentCheck(file)) {
        list.add(AddScheme(file))
      }
    }
  }
}

internal data class UpdateScheme(val file: VirtualFile) : SchemeChangeEvent {
  override fun execute(schemaLoader: Lazy<SchemeLoader<Any, Any>>, schemeManager: SchemeManagerImpl<Any, Any>) {
  }
}

private data class AddScheme(private val file: VirtualFile) : SchemeChangeEvent {
  override fun execute(schemaLoader: Lazy<SchemeLoader<Any, Any>>, schemeManager: SchemeManagerImpl<Any, Any>) {
    if (!file.isValid) {
      return
    }

    val readScheme = readSchemeFromFile(file, schemaLoader.value, schemeManager) ?: return
    val readSchemeKey = schemeManager.processor.getSchemeKey(readScheme)
    val existingScheme = schemeManager.findSchemeByName(readSchemeKey) ?: return
    if (schemeManager.schemeListManager.readOnlyExternalizableSchemes.get(
        schemeManager.processor.getSchemeKey(existingScheme)) !== existingScheme) {
      LOG.warn("Ignore incorrect VFS create scheme event: schema ${readSchemeKey} is already exists")
      return
    }
  }
}

private data class RemoveScheme(private val fileName: String) : SchemeChangeEvent {
  override fun execute(schemaLoader: Lazy<SchemeLoader<Any, Any>>, schemeManager: SchemeManagerImpl<Any, Any>) {
    val scheme = findExternalizableSchemeByFileName(fileName, schemeManager) ?: return
    schemeManager.removeScheme(scheme)
    schemeManager.processor.onSchemeDeleted(scheme)
  }
}

private class RemoveAllSchemes : SchemeChangeEvent {
  override fun execute(schemaLoader: Lazy<SchemeLoader<Any, Any>>, schemeManager: SchemeManagerImpl<Any, Any>) {
    schemeManager.cachedVirtualDirectory = null
    schemeManager.removeExternalizableSchemes()
  }
}