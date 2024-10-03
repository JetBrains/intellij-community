// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer

import com.intellij.platform.ml.embeddings.indexer.configuration.EmbeddingsConfiguration.Companion.getStorageManagerWrapper
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableEntity
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableClass
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableFile
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableSymbol
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableAction
import com.intellij.platform.ml.embeddings.settings.EmbeddingIndexSettingsImpl
import kotlinx.coroutines.channels.Channel

enum class IndexId {
  ACTIONS, FILES, CLASSES, SYMBOLS;

  override fun toString(): String = name.lowercase()

  fun createEntitiesChannel(): Channel<IndexableEntity>? {
    return if (isEnabled())
      Channel<IndexableEntity>(capacity = getStorageManagerWrapper(this).getBatchSize() * BATCHES_IN_BUFFER)
    else null
  }

  fun isEnabled(): Boolean {
    return EmbeddingIndexSettingsImpl.getInstance().run {
      when (this@IndexId) {
        ACTIONS -> shouldIndexActions
        FILES -> shouldIndexFiles
        CLASSES -> shouldIndexClasses
        SYMBOLS -> shouldIndexSymbols
      }
    }
  }

  companion object {
    fun fromIndexableEntity(entity: IndexableEntity): IndexId {
      return when (entity) {
        is IndexableAction -> ACTIONS
        is IndexableFile -> FILES
        is IndexableClass -> CLASSES
        is IndexableSymbol -> SYMBOLS
        else -> throw IllegalArgumentException("Unknown indexable entity class ${entity.javaClass.name}")
      }
    }
  }
}

private const val BATCHES_IN_BUFFER = 8