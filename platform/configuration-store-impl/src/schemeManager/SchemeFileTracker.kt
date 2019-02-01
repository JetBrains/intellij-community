// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore.schemeManager

import com.intellij.configurationStore.LOG
import com.intellij.configurationStore.LazySchemeProcessor
import com.intellij.configurationStore.SchemeContentChangedHandler
import com.intellij.configurationStore.StoreAwareProjectManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.SmartList
import com.intellij.util.io.systemIndependentPath
import java.util.function.Function

internal interface SchemeChangeEvent {
  fun execute(schemeTracker: SchemeFileTracker, schemaLoader: Lazy<SchemeLoader<Any, Any>>)
}

internal class SchemeFileTracker(private val schemeManager: SchemeManagerImpl<Any, Any>, private val project: Project) : BulkFileListener {
  private fun isMyFileWithoutParentCheck(file: VirtualFile) = schemeManager.canRead(file.nameSequence)

  private fun isMyDirectory(parent: VirtualFile): Boolean {
    val virtualDirectory = schemeManager.cachedVirtualDirectory
    return when (virtualDirectory) {
      null -> schemeManager.ioDirectory.systemIndependentPath == parent.path
      else -> virtualDirectory == parent
    }
  }

  private fun findExternalizableSchemeByFileName(fileName: String) = schemeManager.schemes.firstOrNull { fileName == "${schemeManager.getFileName(it)}${schemeManager.schemeExtension}" }

  private class RemoveAllSchemes : SchemeChangeEvent {
    override fun execute(schemeTracker: SchemeFileTracker, schemaLoader: Lazy<SchemeLoader<Any, Any>>) {
      schemeTracker.schemeManager.cachedVirtualDirectory = null
      schemeTracker.schemeManager.removeExternalizableSchemes()
    }
  }

  private data class RemoveScheme(private val fileName: String) : SchemeChangeEvent {
    override fun execute(schemeTracker: SchemeFileTracker, schemaLoader: Lazy<SchemeLoader<Any, Any>>) {
      val scheme = schemeTracker.findExternalizableSchemeByFileName(fileName)
      if (scheme != null) {
        schemeTracker.schemeManager.removeScheme(scheme)
        schemeTracker.schemeManager.processor.onSchemeDeleted(scheme)
      }
    }
  }

  private data class AddScheme(private val file: VirtualFile) : SchemeChangeEvent {
    override fun execute(schemeTracker: SchemeFileTracker, schemaLoader: Lazy<SchemeLoader<Any, Any>>) {
      if (file.isValid) {
        schemeTracker.schemeCreatedExternally(file, schemaLoader.value)
      }
    }

    private fun SchemeFileTracker.schemeCreatedExternally(file: VirtualFile, schemeLoader: SchemeLoader<Any, Any>) {
      val readScheme = readSchemeFromFile(file, schemeLoader) ?: return
      val readSchemeKey = schemeManager.processor.getSchemeKey(readScheme)
      val existingScheme = schemeManager.findSchemeByName(readSchemeKey) ?: return
      if (schemeManager.schemeListManager.readOnlyExternalizableSchemes.get(schemeManager.processor.getSchemeKey(existingScheme)) !== existingScheme) {
        LOG.warn("Ignore incorrect VFS create scheme event: schema ${readSchemeKey} is already exists")
        return
      }
    }
  }

  private data class UpdateScheme(val file: VirtualFile) : SchemeChangeEvent {
    override fun execute(schemeTracker: SchemeFileTracker, schemaLoader: Lazy<SchemeLoader<Any, Any>>) {
    }
  }

  private fun readSchemeFromFile(file: VirtualFile, schemeLoader: SchemeLoader<Any, Any>): Any? {
    val fileName = file.name
    if (file.isDirectory || !schemeManager.canRead(fileName)) {
      return null
    }

    catchAndLog(fileName) {
      return file.inputStream.use {
        schemeLoader.loadScheme(fileName, it)
      }
    }

    return null
  }

  internal fun reload(events: Collection<SchemeChangeEvent>, schemaLoaderRef: Ref<SchemeLoader<Any, Any>>) {
    val oldActiveScheme = schemeManager.activeScheme
    var newActiveScheme: Any? = null

    val lazySchemaLoader = lazy {
      var result = schemaLoaderRef.get()
      if (result == null) {
        result = schemeManager.createSchemeLoader()
        schemaLoaderRef.set(result)
      }
      result
    }

    val processor = schemeManager.processor
    for (event in events) {
      event.execute(this@SchemeFileTracker, lazySchemaLoader)

      if (event !is UpdateScheme) {
        continue
      }

      val file = event.file
      if (!file.isValid) {
        continue
      }

      val fileName = file.name
      val changedScheme = findExternalizableSchemeByFileName(fileName)
      if (callSchemeContentChangedIfSupported(changedScheme, fileName, file)) {
        continue
      }

      changedScheme?.let {
        schemeManager.removeScheme(it)
        processor.onSchemeDeleted(it)
      }

      val newScheme = readSchemeFromFile(file, lazySchemaLoader.value)
      fun isNewActiveScheme(): Boolean {
        if (newActiveScheme != null) {
          return false
        }

        if (oldActiveScheme == null) {
          return newScheme != null && schemeManager.currentPendingSchemeName == processor.getSchemeKey(newScheme)
        }
        else {
          // do not set active scheme if currently no active scheme
          // must be equals by reference
          return changedScheme === oldActiveScheme
        }
      }

      if (isNewActiveScheme()) {
        // call onCurrentSchemeSwitched only when all schemes reloaded
        newActiveScheme = newScheme
      }
    }

    if (newActiveScheme != null) {
      schemeManager.activeScheme = newActiveScheme
      processor.onCurrentSchemeSwitched(oldActiveScheme, newActiveScheme)
    }
  }

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
      (ProjectManager.getInstance() as StoreAwareProjectManager).registerChangedSchemes(list, this, project)
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

  private fun callSchemeContentChangedIfSupported(changedScheme: Any?, fileName: String, file: VirtualFile): Boolean {
    if (changedScheme == null || schemeManager.processor !is SchemeContentChangedHandler<*> || schemeManager.processor !is LazySchemeProcessor) {
      return false
    }

    // unrealistic case, but who knows
    val externalInfo = schemeManager.schemeToInfo.get(changedScheme) ?: return false

    catchAndLog(fileName) {
      val bytes = file.contentsToByteArray()
      lazyPreloadScheme(bytes, schemeManager.isOldSchemeNaming) { name, parser ->
        val attributeProvider = Function<String, String?> { parser.getAttributeValue(null, it) }
        val schemeName = name
                         ?: schemeManager.processor.getSchemeKey(attributeProvider, FileUtilRt.getNameWithoutExtension(fileName))
                         ?: throw nameIsMissed(bytes)

        val dataHolder = SchemeDataHolderImpl(schemeManager.processor, bytes, externalInfo)
        @Suppress("UNCHECKED_CAST")
        (schemeManager.processor as SchemeContentChangedHandler<Any>).schemeContentChanged(changedScheme, schemeName, dataHolder)
      }
      return true
    }
    return false
  }
}