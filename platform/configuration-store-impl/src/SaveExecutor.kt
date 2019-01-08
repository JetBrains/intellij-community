// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.SaveSessionAndFile
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.SmartList
import java.util.*

interface SaveExecutor {
  /**
   * @return was something really saved
   */
  fun save(readonlyFiles: MutableList<SaveSessionAndFile> = SmartList(), errors: MutableList<Throwable>): Boolean
}

internal open class SaveSessionProducerManager : SaveExecutor {
  private val producers = LinkedHashMap<StateStorage, SaveSessionProducer>()

  fun getProducer(storage: StateStorage): SaveSessionProducer? {
    var producer = producers.get(storage)
    if (producer == null) {
      producer = storage.createSaveSessionProducer() ?: return null
      producers.put(storage, producer)
    }
    return producer
  }

  inline fun processSaveSessions(processor: (SaveSession) -> Unit): Boolean {
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

  override fun save(readonlyFiles: MutableList<SaveSessionAndFile>, errors: MutableList<Throwable>): Boolean {
    return processSaveSessions {
      executeSave(it, readonlyFiles, errors)
    }
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