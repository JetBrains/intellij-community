// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.configuration

import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ml.embeddings.indexer.keys.EmbeddingStorageKeyProvider
import com.intellij.platform.ml.embeddings.indexer.keys.EntityIdStorageKeyProvider
import com.intellij.platform.ml.embeddings.indexer.storage.InProcessTextEmbeddingsStorageManager
import com.intellij.platform.ml.embeddings.indexer.storage.TextEmbeddingsStorageManager
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId

class InProcessEmbeddingsConfiguration: EmbeddingsConfiguration<EntityId> {
  override fun getStorageManager(): TextEmbeddingsStorageManager<EntityId> {
    return InProcessTextEmbeddingsStorageManager.getInstance()
  }

  override fun getKeyProvider(): EmbeddingStorageKeyProvider<EntityId> {
    return EntityIdStorageKeyProvider()
  }

  override fun isEnabled(): Boolean {
    return !Registry.`is`("intellij.platform.ml.embeddings.use.native.server")
  }
}