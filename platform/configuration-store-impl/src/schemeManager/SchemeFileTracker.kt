// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore.schemeManager

import com.intellij.configurationStore.*
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

internal class SchemeFileTracker<out T : Any, in MUTABLE_SCHEME : T>(private val schemeManager: SchemeManagerImpl<T, MUTABLE_SCHEME>) : BulkFileListener {
  private fun isMy(file: VirtualFile) = schemeManager.canRead(file.nameSequence)

  private fun isMyDirectory(parent: VirtualFile) = schemeManager.cachedVirtualDirectory.let { if (it == null) schemeManager.ioDirectory.systemIndependentPath == parent.path else it == parent }

  @Suppress("UNCHECKED_CAST")
  private fun findExternalizableSchemeByFileName(fileName: String) = schemeManager.schemes.firstOrNull { fileName == "${schemeManager.getFileName(it)}${schemeManager.schemeExtension}" } as MUTABLE_SCHEME?

  private fun readSchemeFromFile(file: VirtualFile, schemes: MutableList<T>): MUTABLE_SCHEME? {
    val fileName = file.name
    if (file.isDirectory || !schemeManager.canRead(fileName)) {
      return null
    }

    catchAndLog(fileName) {
      return file.inputStream.use { schemeManager.loadScheme(fileName, it, schemes) }
    }

    return null
  }

  override fun after(events: MutableList<out VFileEvent>) {
    eventLoop@ for (event in events) {
      if (event.requestor is SchemeManagerImpl<*, *>) {
        continue
      }

      when (event) {
        is VFileContentChangeEvent -> {
          val fileName = event.file.name
          if (!schemeManager.canRead(fileName) || !isMyDirectory(event.file.parent)) {
            continue@eventLoop
          }

          val oldCurrentScheme = schemeManager.activeScheme
          val changedScheme = findExternalizableSchemeByFileName(fileName)

          if (callSchemeContentChangedIfSupported(changedScheme, fileName, event.file)) {
            continue@eventLoop
          }

          changedScheme?.let {
            schemeManager.removeScheme(it)
            schemeManager.processor.onSchemeDeleted(it)
          }

          schemeManager.updateCurrentScheme(oldCurrentScheme, readSchemeFromFile(event.file, schemeManager.schemes)?.let {
            schemeManager.processor.initScheme(it)
            schemeManager.processor.onSchemeAdded(it)
            it
          })
        }

        is VFileCreateEvent -> {
          if (schemeManager.canRead(event.childName)) {
            if (isMyDirectory(event.parent)) {
              event.file?.let { schemeCreatedExternally(it) }
            }
          }
          else if (event.file?.isDirectory == true) {
            val dir = schemeManager.virtualDirectory
            if (event.file == dir) {
              for (file in dir!!.children) {
                if (isMy(file)) {
                  schemeCreatedExternally(file)
                }
              }
            }
          }
        }
        is VFileDeleteEvent -> {
          val oldCurrentScheme = schemeManager.activeScheme
          if (event.file.isDirectory) {
            val dir = schemeManager.virtualDirectory
            if (event.file == dir) {
              schemeManager.cachedVirtualDirectory = null
              schemeManager.removeExternalizableSchemes()
            }
          }
          else if (isMy(event.file) && isMyDirectory(event.file.parent)) {
            val scheme = findExternalizableSchemeByFileName(event.file.name) ?: continue@eventLoop
            schemeManager.removeScheme(scheme)
            schemeManager.processor.onSchemeDeleted(scheme)
          }

          schemeManager.updateCurrentScheme(oldCurrentScheme)
        }
      }
    }
  }

  private fun callSchemeContentChangedIfSupported(changedScheme: MUTABLE_SCHEME?, fileName: String, file: VirtualFile): Boolean {
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
        (schemeManager.processor as SchemeContentChangedHandler<MUTABLE_SCHEME>).schemeContentChanged(changedScheme, schemeName, dataHolder)
      }
      return true
    }
    return false
  }

  private fun schemeCreatedExternally(file: VirtualFile) {
    val newSchemes = SmartList<T>()
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