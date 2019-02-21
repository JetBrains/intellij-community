// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore.schemeManager

import com.intellij.configurationStore.LazySchemeProcessor
import com.intellij.configurationStore.SchemeContentChangedHandler
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import java.util.function.Function

internal interface SchemeChangeEvent {
  fun execute(schemaLoader: Lazy<SchemeLoader<Any, Any>>, schemeManager: SchemeManagerImpl<Any, Any>)
}

internal fun findExternalizableSchemeByFileName(fileName: String, schemeManager: SchemeManagerImpl<Any, Any>): Any? {
  return schemeManager.schemes.firstOrNull {
    fileName == "${schemeManager.getFileName(it)}${schemeManager.schemeExtension}"
  }
}

internal fun readSchemeFromFile(file: VirtualFile, schemeLoader: SchemeLoader<Any, Any>, schemeManager: SchemeManagerImpl<Any, Any>): Any? {
  val fileName = file.name
  if (file.isDirectory || !schemeManager.canRead(fileName)) {
    return null
  }

  catchAndLog(fileName) {
    schemeLoader.loadScheme(fileName, null, file.contentsToByteArray())
  }

  return null
}

internal class SchemeChangeApplicator(private val schemeManager: SchemeManagerImpl<Any, Any>) {
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
      event.execute(lazySchemaLoader, schemeManager)

      if (event !is UpdateScheme) {
        continue
      }

      val file = event.file
      if (!file.isValid) {
        continue
      }

      val fileName = file.name
      val changedScheme = findExternalizableSchemeByFileName(fileName, schemeManager)
      if (callSchemeContentChangedIfSupported(changedScheme, fileName, file, schemeManager)) {
        continue
      }

      changedScheme?.let {
        schemeManager.removeScheme(it)
        processor.onSchemeDeleted(it)
      }

      val newScheme = readSchemeFromFile(file, lazySchemaLoader.value, schemeManager)

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
}

private fun callSchemeContentChangedIfSupported(changedScheme: Any?, fileName: String, file: VirtualFile, schemeManager: SchemeManagerImpl<Any, Any>): Boolean {
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