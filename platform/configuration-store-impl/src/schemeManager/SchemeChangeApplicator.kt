// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.schemeManager

import com.intellij.configurationStore.LazySchemeProcessor
import com.intellij.configurationStore.SchemeContentChangedHandler
import com.intellij.openapi.options.Scheme
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.VisibleForTesting
import java.util.function.Function

internal sealed interface SchemeChangeEvent<T : Scheme, M : T> {
  fun execute(schemaLoader: Lazy<SchemeLoader<T, M>>, schemeManager: SchemeManagerImpl<T, M>)
}

internal sealed interface SchemeAddOrUpdateEvent {
  val file: VirtualFile
}

internal fun <T : Scheme, M : T> getSchemeFileName(schemeManager: SchemeManagerImpl<T, M>, scheme: T): String =
  "${schemeManager.getFileName(scheme)}${schemeManager.schemeExtension}"

internal fun <T : Scheme, M : T> readSchemeFromFile(file: VirtualFile, schemeLoader: SchemeLoader<T, M>, schemeManager: SchemeManagerImpl<T, M>): T? {
  val fileName = file.name
  if (file.isDirectory || !schemeManager.canRead(fileName)) {
    return null
  }
  return catchAndLog({ file.path }) {
    schemeLoader.loadScheme(fileName, input = null, file.contentsToByteArray())
  }
}

internal class SchemeChangeApplicator<T : Scheme, M : T>(private val schemeManager: SchemeManagerImpl<T, M>) {
  fun reload(events: Collection<SchemeChangeEvent<T, M>>) {
    val lazySchemeLoader = lazy { schemeManager.createSchemeLoader() }
    doReload(events, lazySchemeLoader)
    if (lazySchemeLoader.isInitialized()) {
      lazySchemeLoader.value.apply()
    }
  }

  private fun doReload(events: Collection<SchemeChangeEvent<T, M>>, lazySchemaLoader: Lazy<SchemeLoader<T, M>>) {
    val oldActiveScheme = schemeManager.activeScheme
    var newActiveScheme: T? = null

    val processor = schemeManager.processor
    for (event in sortSchemeChangeEvents(events)) {
      event.execute(lazySchemaLoader, schemeManager)

      if (event !is UpdateScheme) {
        continue
      }

      val file = event.file
      if (!file.isValid) {
        continue
      }

      val fileName = file.name

      @Suppress("UNCHECKED_CAST")
      val changedScheme = schemeManager.schemes.firstOrNull<T> { getSchemeFileName(schemeManager, it) == fileName } as M?
      if (callSchemeContentChangedIfSupported(changedScheme, fileName, file, schemeManager)) {
        continue
      }

      if (changedScheme != null) {
        lazySchemaLoader.value.removeUpdatedScheme(changedScheme)
        processor.onSchemeDeleted(changedScheme)
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
          // do not set an active scheme if currently no active scheme
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
      processor.onCurrentSchemeSwitched(oldScheme = oldActiveScheme, newScheme = newActiveScheme, processChangeSynchronously = false)
    }
  }
}

@VisibleForTesting
internal fun <T : Scheme, M : T> sortSchemeChangeEvents(inputEvents: Collection<SchemeChangeEvent<T, M>>): Collection<SchemeChangeEvent<T, M>> {
  if (inputEvents.size < 2) {
    return inputEvents
  }

  var isThereSomeRemoveEvent = false

  val existingAddOrUpdate = HashSet<String>()
  val removedFileNames = HashSet<String>()
  val result = ArrayList(inputEvents)
  // first, remove any event before `RemoveAllSchemes` and remove `RemoveScheme` events if there is any subsequent add/update
  for (i in (result.size - 1) downTo 0) {
    val event = result[i]
    if (event is RemoveAllSchemes) {
      for (j in (i - 1) downTo 0) {
        result.removeAt(j)
      }
      break
    }
    else if (event is SchemeAddOrUpdateEvent) {
      val fileName = event.file.name
      if (removedFileNames.contains(fileName)) {
        result.removeAt(i)
      }
      else {
        existingAddOrUpdate.add(fileName)
      }
    }
    else if (event is RemoveScheme) {
      if (existingAddOrUpdate.contains(event.fileName)) {
        result.removeAt(i)
      }
      else {
        isThereSomeRemoveEvent = true
        removedFileNames.add(event.fileName)
      }
    }
  }

  fun weight(event: SchemeChangeEvent<T, M>): Int = if (event is SchemeAddOrUpdateEvent) 1 else 0

  if (isThereSomeRemoveEvent) {
    // second, move all `RemoveScheme` events to the top - to ensure that `SchemeLoader` won't be created during processing of `RemoveScheme` events
    // (because `RemoveScheme` removes schemes from the scheme manager directly)
    result.sortWith(Comparator { o1, o2 ->
      weight(o1) - weight(o2)
    })
  }

  return result
}

private fun <T : Scheme, M : T> callSchemeContentChangedIfSupported(
  changedScheme: M?,
  fileName: String,
  file: VirtualFile,
  schemeManager: SchemeManagerImpl<T, M>
): Boolean {
  if (changedScheme == null || schemeManager.processor !is SchemeContentChangedHandler<*> || schemeManager.processor !is LazySchemeProcessor) {
    return false
  }

  // unrealistic case, but who knows
  val externalInfo = schemeManager.schemeListManager.getExternalInfo(changedScheme) ?: return false
  return catchAndLog({ file.path }) {
    val bytes = file.contentsToByteArray()
    lazyPreloadScheme(bytes, schemeManager.isOldSchemeNaming) { name, parser ->
      val attributeProvider = Function<String, String?> { parser.getAttributeValue(null, it) }
      val schemeName = name
                       ?: schemeManager.processor.getSchemeKey(attributeProvider, FileUtilRt.getNameWithoutExtension(fileName))
                       ?: throw nameIsMissed(bytes)
      val dataHolder = SchemeDataHolderImpl(schemeManager.processor, bytes, externalInfo)
      @Suppress("UNCHECKED_CAST")
      (schemeManager.processor as SchemeContentChangedHandler<M>).schemeContentChanged(changedScheme, schemeName, dataHolder)
    }
    true
  } == true
}
