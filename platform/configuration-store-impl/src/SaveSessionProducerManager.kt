// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CancellationException
import java.util.*

internal open class SaveSessionProducerManager(private val isUseVfsForWrite: Boolean, private val collectVfsEvents: Boolean) {
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
          saveSessionBlocking(saveSession, saveResult)
        }
      }
    }
    else if (EDT.isCurrentThreadEdt()) {
      @Suppress("ForbiddenInSuspectContextMethod")
      ApplicationManager.getApplication().runWriteAction {
        for (saveSession in saveSessions) {
          saveSessionBlocking(saveSession, saveResult)
        }
      }
    }
    else {
      val events = if (collectVfsEvents) ArrayList<VFileEvent>() else null
      val syncList = if (events == null) null else Collections.synchronizedList(events)
      for (saveSession in saveSessions) {
        saveSession(saveSession, syncList, saveResult)
      }
      if (!events.isNullOrEmpty()) {
        blockingContext {
          RefreshQueue.getInstance().processEvents(false, events)
        }
      }
    }
  }

  private fun saveSessionBlocking(saveSession: SaveSession, saveResult: SaveResult) {
    try {
      saveSession.saveBlocking()
    }
    catch (e: ReadOnlyModificationException) {
      LOG.warn(e)
      saveResult.addReadOnlyFile(SaveSessionAndFile(e.session ?: saveSession, e.file))
    }
    catch (e: ProcessCanceledException) { throw e }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) {
      saveResult.addError(e)
    }
  }

  private suspend fun saveSession(saveSession: SaveSession, events: MutableList<VFileEvent>?, saveResult: SaveResult) {
    try {
      saveSession.save(events)
    }
    catch (e: ReadOnlyModificationException) {
      LOG.warn(e)
      saveResult.addReadOnlyFile(SaveSessionAndFile(e.session ?: saveSession, e.file))
    }
    catch (e: ProcessCanceledException) { throw e }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) {
      saveResult.addError(e)
    }
  }
}
