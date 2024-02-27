// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.ml.embeddings.search.indices.InMemoryEmbeddingSearchIndex
import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager.Companion.SEMANTIC_SEARCH_RESOURCES_DIR
import com.intellij.platform.ml.embeddings.utils.generateEmbedding
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.io.File

/**
 * Thread-safe service for semantic actions search.
 * Holds a state with embeddings for each available action and persists it on disk after calculation.
 * Generates the embeddings for actions not present in the loaded state at the IDE startup event if semantic action search is enabled
 */
@Service(Service.Level.APP)
class ActionEmbeddingsStorage : EmbeddingsStorage {
  val index = InMemoryEmbeddingSearchIndex(
    File(PathManager.getSystemPath())
      .resolve(SEMANTIC_SEARCH_RESOURCES_DIR)
      .resolve(LocalArtifactsManager.getInstance().getModelVersion())
      .resolve(INDEX_DIR).toPath()
  )

  @RequiresBackgroundThread
  override suspend fun searchNeighbours(text: String, topK: Int, similarityThreshold: Double?): List<ScoredText> {
    ActionEmbeddingStorageManager.getInstance().triggerIndexing() // trigger indexing on first search usage
    if (index.size == 0) return emptyList()
    val embedding = generateEmbedding(text) ?: return emptyList()
    return index.findClosest(searchEmbedding = embedding, topK = topK, similarityThreshold = similarityThreshold)
  }

  @RequiresBackgroundThread
  suspend fun streamSearchNeighbours(text: String, similarityThreshold: Double? = null): Sequence<ScoredText> {
    ActionEmbeddingStorageManager.getInstance().triggerIndexing() // trigger indexing on first search usage
    if (index.size == 0) return emptySequence()
    val embedding = generateEmbedding(text) ?: return emptySequence()
    return index.streamFindClose(embedding, similarityThreshold)
  }

  companion object {
    private const val INDEX_DIR = "actions"

    fun getInstance(): ActionEmbeddingsStorage = service()
  }
}