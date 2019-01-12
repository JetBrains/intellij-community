// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.async.coroutineDispatchingContext
import com.intellij.openapi.application.async.inUndoTransparentAction
import com.intellij.openapi.application.async.inWriteAction
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.SaveSessionAndFile
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.SmartList
import kotlinx.coroutines.withContext
import java.util.*

interface SaveExecutor {
  /**
   * @return was something really saved
   */
  suspend fun save(readonlyFiles: MutableList<SaveSessionAndFile> = SmartList(), errors: MutableList<Throwable>): Boolean
}

internal open class SaveSessionProducerManager : SaveExecutor {
  private val producers = LinkedHashMap<StateStorage, SaveSessionProducer>()

  // actually, all storages for component store shares the same value, but for flexibility and to simplify code, just compute on the fly
  private var isVfsRequired = false

  fun getProducer(storage: StateStorage): SaveSessionProducer? {
    var producer = producers.get(storage)
    if (producer == null) {
      producer = storage.createSaveSessionProducer() ?: return null
      producers.put(storage, producer)
      if (storage.isUseVfsForWrite) {
        isVfsRequired = true
      }
    }
    return producer
  }

  private inline fun processSaveSessions(processor: (SaveSession) -> Unit): Boolean {
    if (producers.isEmpty()) {
      return false
    }

    var isChanged = false
    for (session in producers.values) {
      processor(session.createSaveSession() ?: continue)
      isChanged = true
    }
    return isChanged
  }

  fun collectSaveSessions(result: MutableList<SaveSession>) {
    processSaveSessions {
      result.add(it)
    }
  }

  override suspend fun save(readonlyFiles: MutableList<SaveSessionAndFile>, errors: MutableList<Throwable>): Boolean {
    val saveSessions = SmartList<SaveSession>()
    collectSaveSessions(saveSessions)
    if (saveSessions.isEmpty()) {
      return false
    }

    val task = { saveSessions(saveSessions, readonlyFiles, errors) }
    if (isVfsRequired) {
      withContext(AppUIExecutor.onUiThread().inUndoTransparentAction().inWriteAction().coroutineDispatchingContext()) {
        task()
      }
    }
    else {
      task()
    }
    return true
  }
}

internal fun saveSessions(saveSessions: MutableList<SaveSession>, readonlyFiles: MutableList<SaveSessionAndFile>, errors: MutableList<Throwable>) {
  for (saveSession in saveSessions) {
    executeSave(saveSession, readonlyFiles, errors)
  }
}

internal fun executeSave(session: SaveSession, readonlyFiles: MutableList<SaveSessionAndFile>, errors: MutableList<Throwable>) {
  try {
    session.save()
  }
  catch (e: ReadOnlyModificationException) {
    LOG.warn(e)
    readonlyFiles.add(SaveSessionAndFile(e.session ?: session, e.file))
  }
  catch (e: ProcessCanceledException) {
    throw e
  }
  catch (e: Exception) {
    errors.add(e)
  }
}