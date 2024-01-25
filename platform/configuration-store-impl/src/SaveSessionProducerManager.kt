// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.SaveSessionAndFile
import com.intellij.openapi.progress.ProcessCanceledException
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
open class SaveSessionProducerManager {
  private val producers = Collections.synchronizedMap(LinkedHashMap<StateStorage, SaveSessionProducer>())

  // withing a single component store, individual storages might be heterogeneous, hence computing on the fly
  private var isVfsRequired = false

  fun getProducer(storage: StateStorage): SaveSessionProducer? {
    var producer = producers[storage]
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

  suspend fun save(): SaveResult {
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

    val saveResult = SaveResult()
    saveSessions(saveSessions, saveResult)
    return saveResult
  }

  protected suspend fun saveSessions(saveSessions: Collection<SaveSession>, saveResult: SaveResult) {
    if (isVfsRequired) {
      writeAction {
        for (saveSession in saveSessions) {
          executeSaveBlocking(saveSession, saveResult)
        }
      }
    }
    else {
      for (saveSession in saveSessions) {
        executeSave(saveSession, saveResult)
      }
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
    catch (e: ProcessCanceledException) { throw e }
    catch (e: CancellationException) { throw e }
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
    catch (e: ProcessCanceledException) { throw e }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) {
      result.addError(e)
    }
  }
}
