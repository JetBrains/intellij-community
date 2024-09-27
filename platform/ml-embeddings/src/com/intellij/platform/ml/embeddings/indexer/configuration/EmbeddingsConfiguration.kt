// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.ml.embeddings.indexer.keys.EmbeddingStorageKeyProvider
import com.intellij.platform.ml.embeddings.indexer.storage.EmbeddingsStorageManagerWrapper
import com.intellij.platform.ml.embeddings.indexer.storage.TextEmbeddingsStorageManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.OverrideOnly
interface EmbeddingsConfiguration<KeyT> {
  fun getStorageManager(): TextEmbeddingsStorageManager<KeyT>

  fun getKeyProvider(): EmbeddingStorageKeyProvider<KeyT>

  fun isEnabled(): Boolean

  companion object {
    private val EP_NAME: ExtensionPointName<EmbeddingsConfiguration<*>> = ExtensionPointName.create(
      "com.intellij.platform.ml.embeddings.textEmbeddingsConfiguration")

    fun getStorageManagerWrapper(): EmbeddingsStorageManagerWrapper<*> {
      val instance = EP_NAME.extensionList.first {
        it.isEnabled()
      }

      return instance.toStorageManagerWrapper()
    }

    private fun <KeyT> EmbeddingsConfiguration<KeyT>.toStorageManagerWrapper(): EmbeddingsStorageManagerWrapper<KeyT> {
      val storageManager = getStorageManager()
      val keyProvider = getKeyProvider()
      return EmbeddingsStorageManagerWrapper(storageManager, keyProvider)
    }
  }
}
