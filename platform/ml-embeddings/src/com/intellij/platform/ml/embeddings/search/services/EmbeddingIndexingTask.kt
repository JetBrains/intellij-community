// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.platform.ml.embeddings.search.indices.DiskSynchronizedEmbeddingSearchIndex
import com.intellij.platform.ml.embeddings.search.indices.EntitySourceType
import com.intellij.platform.ml.embeddings.utils.generateEmbedding
import com.intellij.platform.ml.embeddings.utils.generateEmbeddings

sealed interface EmbeddingIndexingTask {
  suspend fun run(index: DiskSynchronizedEmbeddingSearchIndex)

  class Add(
    private val ids: List<String>,
    private val texts: List<String>,
    private val sourceType: EntitySourceType,
    private val callback: () -> Unit = {}
  ) : EmbeddingIndexingTask {
    override suspend fun run(index: DiskSynchronizedEmbeddingSearchIndex) {
      if (!EmbeddingIndexMemoryManager.getInstance().checkCanAddEntry()) return
      val embeddings = generateEmbeddings(texts) ?: return
      index.addEntries(values = ids zip embeddings, sourceType = sourceType)
      callback()
    }
  }

  @Suppress("unused")
  class AddDiskSynchronized(
    private val ids: List<String>,
    private val texts: List<String>,
    private val sourceType: EntitySourceType,
    private val callback: () -> Unit = {}
  ) : EmbeddingIndexingTask {
    override suspend fun run(index: DiskSynchronizedEmbeddingSearchIndex) {
      if (!EmbeddingIndexMemoryManager.getInstance().checkCanAddEntry()) return
      val embeddings = generateEmbeddings(texts) ?: return
      (ids zip embeddings).forEach { index.addEntry(id = it.first, sourceType = sourceType, embedding = it.second) }
      callback()
    }
  }

  @Suppress("unused")
  class DeleteDiskSynchronized(
    private val ids: List<String>,
    private val callback: () -> Unit = {}
  ) : EmbeddingIndexingTask {
    override suspend fun run(index: DiskSynchronizedEmbeddingSearchIndex) {
      ids.forEach { index.deleteEntry(it) }
      callback()
    }
  }

  @Suppress("unused")
  class RenameDiskSynchronized(
    private val oldId: String,
    private val newId: String,
    private val newSourceType: EntitySourceType,
    private val newIndexableRepresentation: String,
    private val callback: () -> Unit = {}
  ) : EmbeddingIndexingTask {
    override suspend fun run(index: DiskSynchronizedEmbeddingSearchIndex) {
      val embedding = generateEmbedding(newIndexableRepresentation) ?: return
      index.updateEntry(id = oldId, newId = newId, newSourceType = newSourceType, embedding = embedding)
      callback()
    }
  }
}