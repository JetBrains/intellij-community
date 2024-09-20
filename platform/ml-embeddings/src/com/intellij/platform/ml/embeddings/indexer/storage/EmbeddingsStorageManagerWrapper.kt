// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.storage

import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.indexer.IndexId
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableEntity
import com.intellij.platform.ml.embeddings.indexer.keys.EmbeddingStorageKeyProvider
import com.intellij.platform.ml.embeddings.utils.ScoredText

class EmbeddingsStorageManagerWrapper<KeyT>(
  private val storageManager: TextEmbeddingsStorageManager<KeyT>,
  private val keyProvider: EmbeddingStorageKeyProvider<KeyT>,
) {
  suspend fun addAbsent(project: Project, indexId: IndexId, entities: List<IndexableEntity>) {
    return storageManager.addAbsent(project, indexId, entities.map {
      IndexEntry(
        keyProvider.findKey(project, indexId, it),
        it.indexableRepresentation.take(INDEXABLE_REPRESENTATION_CHAR_LIMIT)
      )
    })
  }

  suspend fun search(
    project: Project, indexId: IndexId,
    query: String, limit: Int, similarityThreshold: Float? = null,
  ): List<ScoredText> {
    val result = storageManager.search(project, indexId, query, limit, similarityThreshold)
      .map { (id, similarity) -> ScoredText(keyProvider.findEntityId(project, indexId, id), similarity) }
    return result
  }

  suspend fun startIndexingSession(project: Project, indexId: IndexId) {
    storageManager.startIndexingSession(project, indexId)
  }

  suspend fun finishIndexingSession(project: Project, indexId: IndexId) {
    storageManager.finishIndexingSession(project, indexId)
  }

  companion object {
    private const val INDEXABLE_REPRESENTATION_CHAR_LIMIT = 64
  }
}