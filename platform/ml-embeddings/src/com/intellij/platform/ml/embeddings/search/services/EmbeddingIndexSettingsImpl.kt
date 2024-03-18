// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write


@Service(Service.Level.PROJECT)
class EmbeddingIndexSettingsImpl(project: Project) : EmbeddingIndexSettings {
  override val shouldIndexFiles: Boolean
    get() = mutex.read { clientSettings.any { it.shouldIndexFiles } }
  override val shouldIndexClasses: Boolean
    get() = mutex.read { clientSettings.any { it.shouldIndexClasses } }
  override val shouldIndexSymbols: Boolean
    get() = mutex.read { clientSettings.any { it.shouldIndexSymbols } }

  val shouldIndexAnything: Boolean
    get() = mutex.read { shouldIndexFiles || shouldIndexClasses || shouldIndexSymbols }

  private val mutex = ReentrantReadWriteLock()
  private val clientSettings = mutableListOf<EmbeddingIndexSettings>()

  fun registerClientSettings(settings: EmbeddingIndexSettings) = mutex.write {
    if (settings in clientSettings) return@write
    clientSettings.add(settings)
  }

  fun unregisterClientSettings(settings: EmbeddingIndexSettings) = mutex.write {
    clientSettings.remove(settings)
  }

  companion object {
    fun getInstance(project: Project): EmbeddingIndexSettingsImpl = project.service()
  }
}