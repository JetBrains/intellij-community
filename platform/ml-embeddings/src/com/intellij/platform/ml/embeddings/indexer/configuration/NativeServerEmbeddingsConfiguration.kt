// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.configuration

import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ml.embeddings.indexer.keys.EmbeddingStorageKeyProvider
import com.intellij.platform.ml.embeddings.indexer.keys.IntegerStorageKeyProvider
import com.intellij.platform.ml.embeddings.indexer.storage.NativeServerTextEmbeddingsStorageManager
import com.intellij.platform.ml.embeddings.indexer.storage.TextEmbeddingsStorageManager

class NativeServerEmbeddingsConfiguration: EmbeddingsConfiguration<Long> {
  override fun getStorageManager(): TextEmbeddingsStorageManager<Long> {
    return NativeServerTextEmbeddingsStorageManager.getInstance()
  }

  override fun getKeyProvider(): EmbeddingStorageKeyProvider<Long> {
    return IntegerStorageKeyProvider.getInstance()
  }

  override fun isEnabled(): Boolean {
    return Registry.`is`("intellij.platform.ml.embeddings.use.native.server")
  }
}