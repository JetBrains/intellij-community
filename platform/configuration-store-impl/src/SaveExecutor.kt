// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.configurationStore

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.SaveSessionAndFile
import com.intellij.openapi.progress.ProcessCanceledException
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus
import java.util.*

internal interface SaveExecutor {
  /**
   * @return was something really saved
   */
  suspend fun save(): SaveResult
}

@ApiStatus.Internal
open class SaveSessionProducerManager : SaveExecutor {
  private val producers = Collections.synchronizedMap(LinkedHashMap<StateStorage, SaveSessionProducer>())

  // actually, all storages for component store share the same value, but for flexibility and to simplify code, compute on the fly
  private var isVfsRequired = false

  fun getProducer(storage: StateStorage): SaveSessionProducer? {
    var producer = producers.get(storage)
    if (producer == null) {
      producer = storage.createSaveSessionProducer() ?: return null
      val prev = producers.put(storage, producer)
      check(prev == null)
      if (storage.isUseVfsForWrite) {
        isVfsRequired = true
      }
    }
    return producer
  }

  internal fun collectSaveSessions(result: MutableCollection<SaveSession>) {
    if (producers.isEmpty()) {
      return
    }

    for (session in producers.values) {
      result.add(session.createSaveSession() ?: continue)
    }
  }

  override suspend fun save(): SaveResult {
    if (producers.isEmpty()) {
      return SaveResult.EMPTY
    }

    val saveSessions = ArrayList<SaveSession>()
    for (session in producers.values) {
      saveSessions.add(session.createSaveSession() ?: continue)
    }

    if (saveSessions.isEmpty()) {
      return SaveResult.EMPTY
    }

    val result = SaveResult()
    if (isVfsRequired) {
      writeAction {
        blockingSaveSessions(saveSessions, result)
      }
    }
    else {
      saveSessions(saveSessions, result)
    }
    return result
  }
}

internal suspend fun saveSessions(saveSessions: Collection<SaveSession>, result: SaveResult) {
  for (saveSession in saveSessions) {
    executeSave(saveSession, result)
  }
}

internal fun blockingSaveSessions(saveSessions: Collection<SaveSession>, result: SaveResult) {
  for (saveSession in saveSessions) {
    executeSaveBlocking(saveSession, result)
  }
}

private suspend fun executeSave(session: SaveSession, result: SaveResult) {
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

private fun executeSaveBlocking(session: SaveSession, result: SaveResult) {
  try {
    session.saveBlocking()
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
