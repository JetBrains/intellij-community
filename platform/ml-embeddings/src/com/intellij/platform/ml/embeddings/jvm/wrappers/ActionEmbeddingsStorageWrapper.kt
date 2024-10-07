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
import com.intellij.platform.ml.embeddings.jvm.wrappers.AbstractEmbeddingsStorageWrapper.Companion.OLD_API_DIR_NAME
import com.intellij.platform.ml.embeddings.utils.generateEmbedding
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.io.path.Path
import kotlin.io.path.div

@Service(Service.Level.APP)
class ActionEmbeddingsStorageWrapper : EmbeddingsStorageWrapper {
  private val index = InMemoryEmbeddingSearchIndex(
    Path(PathManager.getSystemPath())
    / SEMANTIC_SEARCH_RESOURCES_DIR_NAME / OLD_API_DIR_NAME / INDICES_DIR_NAME / INDEX_DIR
  )

  override suspend fun addEntries(values: Iterable<Pair<EntityId, FloatTextEmbedding>>) {
    index.addEntries(values)
  }

  override suspend fun removeEntries(keys: List<EntityId>) {
    for (key in keys) {
      index.remove(key)
    }
  }

  override suspend fun clear() {
    index.clear()
  }

  @RequiresBackgroundThread
  override suspend fun searchNeighbours(queryEmbedding: FloatTextEmbedding, topK: Int, similarityThreshold: Double?): List<ScoredKey<EntityId>> {
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

  override suspend fun startIndexingSession() {
    index.onIndexingStart()
  }

  override suspend fun finishIndexingSession() {
    index.onIndexingFinish()
  }

  override suspend fun getSize(): Int = index.getSize()

  override suspend fun estimateMemory(): Long = index.estimateMemoryUsage()

  suspend fun lookup(id: EntityId): FloatTextEmbedding? = index.lookup(id)

  companion object {
    private const val INDEX_DIR = "actions"

    fun getInstance(): ActionEmbeddingsStorageWrapper = service()
  }
}