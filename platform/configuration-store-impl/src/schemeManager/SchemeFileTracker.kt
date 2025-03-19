// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.schemeManager

import com.intellij.configurationStore.LOG
import com.intellij.configurationStore.RELOADING_STORAGE_WRITE_REQUESTOR
import com.intellij.configurationStore.StoreReloadManager
import com.intellij.configurationStore.StoreReloadManagerImpl
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.options.Scheme
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import kotlin.io.path.invariantSeparatorsPathString

internal class SchemeFileTracker<T : Scheme, M : T>(
  private val schemeManager: SchemeManagerImpl<T, M>,
  private val project: Project
) : BulkFileListener {
  private val applicator = SchemeChangeApplicator(schemeManager)

  override fun after(events: List<VFileEvent>) {
    val list = ArrayList<SchemeChangeEvent<T, M>>()

    for (event in events) {
      if (event.requestor is SchemeManagerImpl<*, *> || event.requestor == RELOADING_STORAGE_WRITE_REQUESTOR) {
        continue
      }

      when (event) {
        is VFileContentChangeEvent -> {
          val file = event.file
          if (isMyFileWithoutParentCheck(file) && file.parent != null && isMyDirectory(file.parent)) {
            LOG.debug { "CHANGED ${file.path}" }
            list.add(UpdateScheme(file))
          }
        }

        is VFileCreateEvent -> {
          if (event.isDirectory) {
            handleDirectoryCreated(event, list)
          }
          else if (schemeManager.canRead(event.childName) && isMyDirectory(event.parent)) {
            val virtualFile = event.file
            LOG.debug { "CREATED ${event.path} (virtualFile: ${if (virtualFile == null) "not " else ""}found)" }
            if (virtualFile != null) {
              list.add(AddScheme(virtualFile))
            }
          }
        }

        is VFileDeleteEvent -> {
          val file = event.file
          if (file.isDirectory) {
            handleDirectoryDeleted(file, list)
          }
          else if (isMyFileWithoutParentCheck(file) && isMyDirectory(file.parent)) {
            LOG.debug { "DELETED ${file.path}" }
            list.add(RemoveScheme(file.name))
          }
        }
      }
    }

    if (list.isNotEmpty()) {
      (StoreReloadManager.getInstance(project) as StoreReloadManagerImpl).registerChangedSchemes(list, applicator)
    }
  }

  private fun isMyFileWithoutParentCheck(file: VirtualFile) = schemeManager.canRead(file.nameSequence)

  @Suppress("MoveVariableDeclarationIntoWhen")
  private fun isMyDirectory(parent: VirtualFile): Boolean {
    val virtualDirectory = schemeManager.cachedVirtualDirectory
    return when (virtualDirectory) {
      null -> schemeManager.ioDirectory.invariantSeparatorsPathString == parent.path
      else -> virtualDirectory == parent
    }
  }

  private fun handleDirectoryDeleted(file: VirtualFile, list: MutableList<SchemeChangeEvent<T, M>>) {
    if (!StringUtil.equals(file.nameSequence, schemeManager.ioDirectory.fileName.toString())) {
      return
    }
    LOG.debug { "DIR DELETED ${file.path}" }
    if (file == schemeManager.getVirtualDirectory()) {
      list.add(RemoveAllSchemes())
    }
  }

  private fun handleDirectoryCreated(event: VFileCreateEvent, list: MutableList<SchemeChangeEvent<T, M>>) {
    if (event.childName != schemeManager.ioDirectory.fileName.toString()) {
      return
    }

    val dir = schemeManager.getVirtualDirectory()
    val virtualFile = event.file
    if (virtualFile != dir) {
      return
    }

    LOG.debug { "DIR CREATED ${virtualFile?.path}" }

    for (file in dir!!.children) {
      if (isMyFileWithoutParentCheck(file)) {
        list.add(AddScheme(file))
      }
    }
  }
}

internal data class UpdateScheme<T : Scheme, M : T>(override val file: VirtualFile) : SchemeChangeEvent<T, M>, SchemeAddOrUpdateEvent {
  override fun execute(schemaLoader: Lazy<SchemeLoader<T, M>>, schemeManager: SchemeManagerImpl<T, M>) { }
}

private data class AddScheme<T : Scheme, M : T>(override val file: VirtualFile) : SchemeChangeEvent<T, M>, SchemeAddOrUpdateEvent {
  override fun execute(schemaLoader: Lazy<SchemeLoader<T, M>>, schemeManager: SchemeManagerImpl<T, M>) {
    if (!file.isValid) {
      return
    }

    val readScheme = readSchemeFromFile(file, schemaLoader.value, schemeManager) ?: return
    val readSchemeKey = schemeManager.processor.getSchemeKey(readScheme)
    val existingScheme = schemeManager.findSchemeByName(readSchemeKey) ?: return
    if (schemeManager.schemeListManager.readOnlyExternalizableSchemes[schemeManager.processor.getSchemeKey(existingScheme)] !== existingScheme) {
      LOG.warn("Ignore incorrect VFS create scheme event: schema $readSchemeKey is already exists")
      return
    }
  }
}

internal data class RemoveScheme<T : Scheme, M : T>(@JvmField val fileName: String) : SchemeChangeEvent<T, M> {
  override fun execute(schemaLoader: Lazy<SchemeLoader<T, M>>, schemeManager: SchemeManagerImpl<T, M>) {
    LOG.assertTrue(!schemaLoader.isInitialized())

    // do not schedule scheme file removing because the file was already removed
    val scheme = schemeManager.removeFirstScheme(isScheduleToDelete = false) {
      fileName == getSchemeFileName(schemeManager, it)
    } ?: return
    @Suppress("UNCHECKED_CAST")
    schemeManager.processor.onSchemeDeleted(scheme as M)
  }
}

internal class RemoveAllSchemes<T : Scheme, M : T> : SchemeChangeEvent<T, M> {
  override fun execute(schemaLoader: Lazy<SchemeLoader<T, M>>, schemeManager: SchemeManagerImpl<T, M>) {
    LOG.assertTrue(!schemaLoader.isInitialized())

    schemeManager.cachedVirtualDirectory = null
    // do not schedule scheme file removing because files were already removed
    schemeManager.removeExternalizableSchemesFromRuntimeState()
  }
}
