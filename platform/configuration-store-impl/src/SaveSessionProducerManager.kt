// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.ExceptionUtil
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CancellationException
import java.nio.file.AccessDeniedException
import java.util.Collections

internal open class SaveSessionProducerManager {
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

  suspend fun save(saveResult: SaveResult, collectVfsEvents: Boolean) {
    if (producers.isNotEmpty()) {
      val saveSessions = ArrayList<SaveSession>()
      collectSaveSessions(saveSessions)
      if (saveSessions.isNotEmpty()) {
        saveSessions(saveSessions, saveResult, collectVfsEvents)
      }
    }
  }
}

internal suspend fun saveSessions(saveSessions: Collection<SaveSession>, saveResult: SaveResult, collectVfsEvents: Boolean) {
  if (EDT.isCurrentThreadEdt()) {
    LOG.error("saveSessions can't be used on EDT")
  }

  val threadUnsafeEventList = if (collectVfsEvents) ArrayList<VFileEvent>() else null
  val events = if (threadUnsafeEventList == null) null else Collections.synchronizedList(threadUnsafeEventList)
  for (saveSession in saveSessions) {
    try {
      saveSession.save(events)
    }
    catch (e: ReadOnlyModificationException) {
      LOG.warn(e)
      saveResult.addReadOnlyFile(SaveSessionAndFile(e.session ?: saveSession, e.file))
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      val sessionAndFile = processAccessDeniedException(saveSession, e)
      if (sessionAndFile == null) {
        saveResult.addError(e)
      }
      else {
        saveResult.addReadOnlyFile(sessionAndFile)
      }
    }
  }

  if (!threadUnsafeEventList.isNullOrEmpty()) {
    RefreshQueue.getInstance().processEvents(threadUnsafeEventList)
  }
}

private fun processAccessDeniedException(saveSession: SaveSession, e: Exception): SaveSessionAndFile? {
  val accessDeniedException = e as? AccessDeniedException ?: ExceptionUtil.findCause(e, AccessDeniedException::class.java) ?: return null
  val filePath = accessDeniedException.file ?: return null
  val file = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return null
  return SaveSessionAndFile(saveSession, file)
}
