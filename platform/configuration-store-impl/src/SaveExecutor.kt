// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.configurationStore

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.SaveSessionAndFile
import com.intellij.openapi.progress.ProcessCanceledException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

internal interface SaveExecutor {
  /**
   * @return was something really saved
   */
  suspend fun save(): SaveResult
}

@ApiStatus.Internal
open class SaveSessionProducerManager : SaveExecutor {
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
    processSaveSessions(result::add)
  }

  override suspend fun save(): SaveResult {
    val saveSessions = ArrayList<SaveSession>()
    collectSaveSessions(saveSessions)
    if (saveSessions.isEmpty()) {
      return SaveResult.EMPTY
    }

    val task = {
      val result = SaveResult()
      saveSessions(saveSessions, result)
      result
    }

    if (isVfsRequired) {
      return withContext(Dispatchers.EDT) {
        runWriteAction(task)
      }
    }
    else {
      return task()
    }
  }
}

internal fun saveSessions(saveSessions: List<SaveSession>, result: SaveResult) {
  for (saveSession in saveSessions) {
    executeSave(saveSession, result)
  }
}

internal fun executeSave(session: SaveSession, result: SaveResult) {
  try {
    session.save()
  }
  catch (e: ReadOnlyModificationException) {
    LOG.warn(e)
    result.addReadOnlyFile(SaveSessionAndFile(e.session ?: session, e.file))
  }
  catch (e: ProcessCanceledException) {
    throw e
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Exception) {
    result.addError(e)
  }
}