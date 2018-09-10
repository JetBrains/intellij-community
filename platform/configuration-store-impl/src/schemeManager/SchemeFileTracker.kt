// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore.schemeManager

import com.intellij.configurationStore.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtilRt
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
  fun SchemeFileTracker.execute()
}

internal class SchemeFileTracker(private val schemeManager: SchemeManagerImpl<Any, Any>, private val project: Project?) : BulkFileListener {
  private fun isMy(file: VirtualFile) = schemeManager.canRead(file.nameSequence)

  private fun isMyDirectory(parent: VirtualFile) = schemeManager.cachedVirtualDirectory.let { if (it == null) schemeManager.ioDirectory.systemIndependentPath == parent.path else it == parent }

  @Suppress("UNCHECKED_CAST")
  private fun findExternalizableSchemeByFileName(fileName: String) = schemeManager.schemes.firstOrNull { fileName == "${schemeManager.getFileName(it)}${schemeManager.schemeExtension}" }

  private val projectManager by lazy { (ProjectManager.getInstance() as StoreAwareProjectManager) }

  private class RemoveAllSchemes : SchemeChangeEvent {
    override fun SchemeFileTracker.execute() {
      schemeManager.cachedVirtualDirectory = null
      schemeManager.removeExternalizableSchemes()
    }
  }

  private data class RemoveScheme(private val fileName: String) : SchemeChangeEvent {
    override fun SchemeFileTracker.execute() {
      val scheme = findExternalizableSchemeByFileName(fileName)
      if (scheme != null) {
        schemeManager.removeScheme(scheme)
        schemeManager.processor.onSchemeDeleted(scheme)
      }
    }
  }

  private data class AddScheme(private val file: VirtualFile) : SchemeChangeEvent {
    override fun SchemeFileTracker.execute() {
      if (file.isValid) {
        schemeCreatedExternally(file)
      }
    }
  }

  private data class UpdateScheme(val file: VirtualFile) : SchemeChangeEvent {
    override fun SchemeFileTracker.execute() {
    }
  }

  private fun readSchemeFromFile(file: VirtualFile, schemes: MutableList<Any>): Any? {
    val fileName = file.name
    if (file.isDirectory || !schemeManager.canRead(fileName)) {
      return null
    }

    catchAndLog(fileName) {
      return file.inputStream.use { schemeManager.loadScheme(fileName, it, schemes) }
    }

    return null
  }

  internal fun reload(events: Collection<SchemeChangeEvent>) {
    val oldActiveScheme = schemeManager.activeScheme
    var newActiveScheme: Any? = null

    val processor = schemeManager.processor
    for (event in events) {
      event.apply {
        execute()
      }

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

      val newScheme = readSchemeFromFile(file, schemeManager.schemes)?.let {
        processor.initScheme(it)
        processor.onSchemeAdded(it)
        it
      }

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
    fun registerChange(schemeEvent: SchemeChangeEvent) {
      if (project == null) {
        // test mode
        reload(listOf(schemeEvent))
      }
      else {
        projectManager.registerChangedScheme(schemeEvent, this, project)
      }
    }

    eventLoop@ for (event in events) {
      if (event.requestor is SchemeManagerImpl<*, *>) {
        continue
      }

      when (event) {
        is VFileContentChangeEvent -> {
          if (schemeManager.canRead(event.file.name) && isMyDirectory(event.file.parent)) {
            registerChange(UpdateScheme(event.file))
          }
        }

        is VFileCreateEvent -> {
          if (schemeManager.canRead(event.childName)) {
            if (isMyDirectory(event.parent)) {
              event.file?.let {
                registerChange(AddScheme(it))
              }
            }
          }
          else if (event.file?.isDirectory == true) {
            val dir = schemeManager.virtualDirectory
            if (event.file == dir) {
              for (file in dir!!.children) {
                if (isMy(file)) {
                  registerChange(AddScheme(file))
                }
              }
            }
          }
        }

        is VFileDeleteEvent -> {
          if (event.file.isDirectory) {
            if (event.file == schemeManager.virtualDirectory) {
              registerChange(RemoveAllSchemes())
            }
          }
          else if (isMy(event.file) && isMyDirectory(event.file.parent)) {
            registerChange(RemoveScheme(event.file.name))
          }
        }
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

  private fun schemeCreatedExternally(file: VirtualFile) {
    val newSchemes = SmartList<Any>()
    val readScheme = readSchemeFromFile(file, newSchemes)
    if (readScheme != null) {
      val readSchemeKey = schemeManager.processor.getSchemeKey(readScheme)
      val existingScheme = schemeManager.findSchemeByName(readSchemeKey)
      @Suppress("SuspiciousEqualsCombination")
      if (existingScheme != null && schemeManager.schemeListManager.readOnlyExternalizableSchemes.get(
          schemeManager.processor.getSchemeKey(existingScheme)) !== existingScheme) {
        LOG.warn("Ignore incorrect VFS create scheme event: schema ${readSchemeKey} is already exists")
        return
      }

      schemeManager.schemes.addAll(newSchemes)

      schemeManager.processor.initScheme(readScheme)
      schemeManager.processor.onSchemeAdded(readScheme)
    }
  }
}