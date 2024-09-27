// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.ml.embeddings.indexer.keys.EmbeddingStorageKeyProvider
import com.intellij.platform.ml.embeddings.indexer.storage.EmbeddingsStorageManagerWrapper
import com.intellij.platform.ml.embeddings.indexer.storage.TextEmbeddingsStorageManager

interface EmbeddingsConfiguration<KeyT> {
  fun getStorageManager(): TextEmbeddingsStorageManager<KeyT>

  fun getKeyProvider(): EmbeddingStorageKeyProvider<KeyT>

  fun isEnabled(): Boolean

  fun toStorageManagerWrapper(): EmbeddingsStorageManagerWrapper<KeyT> {
    return EmbeddingsStorageManagerWrapper(getStorageManager(), getKeyProvider())
  }

  companion object {
    private val EP_NAME: ExtensionPointName<EmbeddingsConfiguration<*>> = ExtensionPointName.create(
      "com.intellij.platform.ml.embeddings.textEmbeddingsConfiguration")

    fun getConfiguration(): EmbeddingsConfiguration<*> {
      return EP_NAME.extensionList.first { it.isEnabled() }
    }
  }
}