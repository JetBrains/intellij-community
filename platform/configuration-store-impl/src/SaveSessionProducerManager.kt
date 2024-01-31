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
open class SaveSessionProducerManager(private val isUseVfsForWrite: Boolean) {
  private val producers = Collections.synchronizedMap(LinkedHashMap<StateStorage, SaveSessionProducer>())

  fun getProducer(storage: StateStorage): SaveSessionProducer? {
    var producer = producers[storage]
    if (producer == null) {
      producer = storage.createSaveSessionProducer() ?: return null
      val prev = producers.put(storage, producer)
      check(prev == null)
    }
    return producer
  }

  internal fun collectSaveSessions(result: MutableCollection<SaveSession>) {
    for (session in producers.values) {
      result.add(session.createSaveSession() ?: continue)
    }
  }

  suspend fun save(saveResult: SaveResult) {
    if (producers.isNotEmpty()) {
      val saveSessions = ArrayList<SaveSession>()
      collectSaveSessions(saveSessions)
      if (saveSessions.isNotEmpty()) {
        saveSessions(saveSessions, saveResult)
      }
    }
  }

  protected suspend fun saveSessions(saveSessions: Collection<SaveSession>, saveResult: SaveResult) {
    if (isUseVfsForWrite) {
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
