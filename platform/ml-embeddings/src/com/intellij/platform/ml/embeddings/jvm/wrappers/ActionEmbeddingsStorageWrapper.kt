// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.jvm.wrappers

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.ml.embeddings.logging.EmbeddingSearchLogger
import com.intellij.platform.ml.embeddings.jvm.indices.InMemoryEmbeddingSearchIndex
import com.intellij.platform.ml.embeddings.indexer.IndexId.ACTIONS
import com.intellij.platform.ml.embeddings.actions.ActionEmbeddingStorageManager
import com.intellij.platform.ml.embeddings.external.artifacts.LocalArtifactsManager.Companion.INDICES_DIR_NAME
import com.intellij.platform.ml.embeddings.jvm.models.LocalEmbeddingServiceProviderImpl
import com.intellij.platform.ml.embeddings.external.artifacts.LocalArtifactsManager.Companion.SEMANTIC_SEARCH_RESOURCES_DIR_NAME
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.platform.ml.embeddings.indexer.storage.ScoredKey
import com.intellij.platform.ml.embeddings.utils.generateEmbedding
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.io.path.Path

@Service(Service.Level.APP)
class ActionEmbeddingsStorageWrapper {
  val index = InMemoryEmbeddingSearchIndex(
    Path(PathManager.getSystemPath())
      .resolve(SEMANTIC_SEARCH_RESOURCES_DIR_NAME)
      .resolve(INDICES_DIR_NAME)
      .resolve(INDEX_DIR)
  )

  @RequiresBackgroundThread
  suspend fun searchNeighbours(queryEmbedding: FloatTextEmbedding, topK: Int, similarityThreshold: Double?): List<ScoredKey<EntityId>> {
    ActionEmbeddingStorageManager.getInstance().triggerIndexing() // trigger indexing on first search usage
    if (index.getSize() == 0) return emptyList()
    val searchStartTime = System.nanoTime()
    LocalEmbeddingServiceProviderImpl.getInstance().scheduleCleanup()
    val neighbours = index.findClosest(searchEmbedding = queryEmbedding, topK = topK, similarityThreshold = similarityThreshold)
    EmbeddingSearchLogger.searchFinished(null, ACTIONS, TimeoutUtil.getDurationMillis(searchStartTime))
    return neighbours
  }

  @RequiresBackgroundThread
  suspend fun streamSearchNeighbours(text: String, similarityThreshold: Double? = null): Flow<ScoredKey<EntityId>> {
    ActionEmbeddingStorageManager.getInstance().triggerIndexing() // trigger indexing on first search usage
    if (index.getSize() == 0) return emptyFlow()
    LocalEmbeddingServiceProviderImpl.getInstance().scheduleCleanup()
    val embedding = generateEmbedding(text) ?: return emptyFlow()
    return index.streamFindClose(embedding, similarityThreshold)
  }

  companion object {
    private const val INDEX_DIR = "actions"

    fun getInstance(): ActionEmbeddingsStorageWrapper = service()
  }
}