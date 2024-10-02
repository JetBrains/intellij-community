// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.storage

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.actions.ActionEmbeddingStorageManager
import com.intellij.platform.ml.embeddings.indexer.FileBasedEmbeddingIndexer
import com.intellij.platform.ml.embeddings.jvm.models.LocalEmbeddingServiceProvider
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.platform.ml.embeddings.indexer.IndexId
import com.intellij.platform.ml.embeddings.jvm.wrappers.ClassEmbeddingsStorageWrapper
import com.intellij.platform.ml.embeddings.jvm.wrappers.FileEmbeddingsStorageWrapper
import com.intellij.platform.ml.embeddings.jvm.wrappers.SymbolEmbeddingsStorageWrapper
import com.intellij.platform.ml.embeddings.jvm.models.LocalEmbeddingServiceProviderImpl
import com.intellij.platform.ml.embeddings.jvm.wrappers.ActionEmbeddingsStorageWrapper
import com.intellij.platform.ml.embeddings.jvm.wrappers.EmbeddingsStorageWrapper

@Service(Service.Level.APP)
class InProcessTextEmbeddingsStorageManager : TextEmbeddingsStorageManager<EntityId> {
  private val modelService: LocalEmbeddingServiceProvider = LocalEmbeddingServiceProviderImpl.getInstance()

  override suspend fun addAbsent(project: Project?, indexId: IndexId, entries: List<IndexEntry<EntityId>>) {
    val embeddingService = modelService.getService() ?: return
    val embeddings = embeddingService.embed(entries.map { it.text })
    getWrapper(project, indexId).addEntries(entries.map { it.key }.zip(embeddings))
  }

  override suspend fun remove(project: Project?, indexId: IndexId, keys: List<EntityId>) {
    getWrapper(project, indexId).removeEntries(keys)
  }

  override suspend fun search(
    project: Project?, indexId: IndexId,
    query: String, limit: Int, similarityThreshold: Float?,
  ): List<ScoredKey<EntityId>> {
    if (indexId == IndexId.ACTIONS) ActionEmbeddingStorageManager.getInstance().triggerIndexing()
    else FileBasedEmbeddingIndexer.getInstance().triggerIndexing(project!!)
    val embeddingService = modelService.getService() ?: return emptyList()
    val queryEmbedding = embeddingService.embed(query)
    val result = if (indexId == IndexId.ACTIONS) {
      ActionEmbeddingsStorageWrapper.getInstance().searchNeighbours(queryEmbedding, limit, similarityThreshold?.toDouble())
    }
    else {
      getWrapper(project, indexId)
        .searchNeighbours(queryEmbedding, limit, similarityThreshold?.toDouble())
        .map { (id, similarity) -> ScoredKey(id, similarity) }
    }

    return result
  }

  override suspend fun clearStorage(project: Project?, indexId: IndexId) {
    getWrapper(project, indexId).clear()
  }

  override suspend fun startIndexingSession(project: Project?, indexId: IndexId) {
    getWrapper(project, indexId).startIndexingSession()
  }

  override suspend fun finishIndexingSession(project: Project?, indexId: IndexId) {
    getWrapper(project, indexId).finishIndexingSession()
  }

  override suspend fun getStorageStats(project: Project?, indexId: IndexId): StorageStats {
    val wrapper = getWrapper(project, indexId)
    return StorageStats(wrapper.getSize(), wrapper.estimateMemory())
  }

  override fun getBatchSize(): Int = 1 // Empirically selected best value

  companion object {
    private fun getWrapper(project: Project?, indexId: IndexId): EmbeddingsStorageWrapper {
      return when (indexId) {
        IndexId.CLASSES -> ClassEmbeddingsStorageWrapper.getInstance(project!!)
        IndexId.FILES -> FileEmbeddingsStorageWrapper.getInstance(project!!)
        IndexId.SYMBOLS -> SymbolEmbeddingsStorageWrapper.getInstance(project!!)
        IndexId.ACTIONS -> ActionEmbeddingsStorageWrapper.getInstance()
        else -> throw IllegalArgumentException("Unsupported index id: $indexId")
      }
    }

    fun getInstance(): InProcessTextEmbeddingsStorageManager = service()
  }
}