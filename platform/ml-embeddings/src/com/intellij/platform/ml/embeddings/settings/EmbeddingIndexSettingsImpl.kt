// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Service(Service.Level.APP)
class EmbeddingIndexSettingsImpl : EmbeddingIndexSettings {
  override val shouldIndexActions: Boolean
    get() = mutex.read { clientSettings.any { it.shouldIndexActions } }
  override val shouldIndexFiles: Boolean
    get() = mutex.read { clientSettings.any { it.shouldIndexFiles } }
  override val shouldIndexClasses: Boolean
    get() = mutex.read { clientSettings.any { it.shouldIndexClasses } }
  override val shouldIndexSymbols: Boolean
    get() = mutex.read { clientSettings.any { it.shouldIndexSymbols } }

  val shouldIndexAnythingFileBased: Boolean
    get() = mutex.read { shouldIndexFiles || shouldIndexClasses || shouldIndexSymbols }

  private val mutex = ReentrantReadWriteLock()
  private val clientSettings = mutableListOf<EmbeddingIndexSettings>()

  fun registerClientSettings(settings: EmbeddingIndexSettings) {
    mutex.write {
      if (settings in clientSettings) return@write
      clientSettings.add(settings)
    }
  }

  @Suppress("unused")
  fun unregisterClientSettings(settings: EmbeddingIndexSettings) {
    mutex.write {
      clientSettings.remove(settings)
    }
  }

  companion object {
    fun getInstance(): EmbeddingIndexSettingsImpl = service()
  }
}