// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.storage

import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.indexer.IndexId
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableEntity
import com.intellij.platform.ml.embeddings.indexer.keys.EmbeddingStorageKeyProvider
import com.intellij.platform.ml.embeddings.utils.ScoredText

class EmbeddingsStorageManagerWrapper<KeyT>(
  private val indexId: IndexId,
  val storageManager: TextEmbeddingsStorageManager<KeyT>,
  val keyProvider: EmbeddingStorageKeyProvider<KeyT>,
) {
  suspend fun addAbsent(project: Project?, entities: List<IndexableEntity>) {
    return storageManager.addAbsent(project, indexId, entities.map {
      IndexEntry(
        keyProvider.findKey(project, indexId, it),
        it.indexableRepresentation.take(INDEXABLE_REPRESENTATION_CHAR_LIMIT)
      )
    })
  }

  suspend fun remove(project: Project?, keys: List<KeyT>) {
    storageManager.remove(project, indexId, keys)
  }

  suspend fun search(
    project: Project,
    query: String,
    limit: Int,
    similarityThreshold: Float? = null,
  ): List<ScoredText> {
    val result = storageManager.search(project, indexId, query, limit, similarityThreshold)
      .map { (id, similarity) ->
        val entityId = keyProvider.findEntityId(project, indexId, id)
        if (entityId.isEmpty()) throw IllegalStateException("Entity id returned from EmbeddingStorageKeyProvider cannot be empty")
        ScoredText(entityId, similarity)
      }
    return result
  }

  suspend fun clearStorage(project: Project?) {
    storageManager.clearStorage(project, indexId)
  }

  suspend fun startIndexingSession(project: Project?) {
    storageManager.startIndexingSession(project, indexId)
  }

  suspend fun finishIndexingSession(project: Project?) {
    storageManager.finishIndexingSession(project, indexId)
  }

  suspend fun getStorageStats(project: Project?): StorageStats {
    return storageManager.getStorageStats(project, indexId)
  }

  fun getBatchSize() = storageManager.getBatchSize()

  companion object {
    private const val INDEXABLE_REPRESENTATION_CHAR_LIMIT = 64
  }
}