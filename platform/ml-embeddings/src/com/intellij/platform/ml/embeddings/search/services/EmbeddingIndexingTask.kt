// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.platform.ml.embeddings.utils.generateEmbedding
import com.intellij.platform.ml.embeddings.utils.generateEmbeddings
import com.intellij.platform.ml.embeddings.search.indices.DiskSynchronizedEmbeddingSearchIndex

sealed interface EmbeddingIndexingTask {
  suspend fun run(index: DiskSynchronizedEmbeddingSearchIndex)

  class Add(
    private val ids: List<String>,
    private val texts: List<String>,
    private val callback: () -> Unit = {}
  ) : EmbeddingIndexingTask {
    override suspend fun run(index: DiskSynchronizedEmbeddingSearchIndex) {
      val embeddings = generateEmbeddings(texts) ?: return
      index.addEntries(ids zip embeddings)
      callback()
    }
  }

  class AddDiskSynchronized(
    private val ids: List<String>,
    private val texts: List<String>,
    private val callback: () -> Unit = {}
  ) : EmbeddingIndexingTask {
    override suspend fun run(index: DiskSynchronizedEmbeddingSearchIndex) {
      val embeddings = generateEmbeddings(texts) ?: return
      (ids zip embeddings).forEach { index.addEntry(it.first, it.second) }
      callback()
    }
  }

  class DeleteDiskSynchronized(
    private val ids: List<String>,
    private val callback: () -> Unit = {}
  ) : EmbeddingIndexingTask {
    override suspend fun run(index: DiskSynchronizedEmbeddingSearchIndex) {
      ids.forEach { index.deleteEntry(it) }
      callback()
    }
  }

  class RenameDiskSynchronized(
    private val oldId: String,
    private val newId: String,
    private val newIndexableRepresentation: String,
    private val callback: () -> Unit = {}
  ) : EmbeddingIndexingTask {
    override suspend fun run(index: DiskSynchronizedEmbeddingSearchIndex) {
      val embedding = generateEmbedding(newIndexableRepresentation) ?: return
      index.updateEntry(oldId, newId, embedding)
      callback()
    }
  }
}