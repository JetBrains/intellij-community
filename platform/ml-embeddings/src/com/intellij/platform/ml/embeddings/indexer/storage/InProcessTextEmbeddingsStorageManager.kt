// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.storage

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.indexer.FileBasedEmbeddingIndexer
import com.intellij.platform.ml.embeddings.jvm.models.LocalEmbeddingServiceProvider
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.platform.ml.embeddings.indexer.IndexId
import com.intellij.platform.ml.embeddings.jvm.wrappers.ClassEmbeddingsStorageWrapper
import com.intellij.platform.ml.embeddings.jvm.wrappers.FileEmbeddingsStorageWrapper
import com.intellij.platform.ml.embeddings.jvm.wrappers.SymbolEmbeddingsStorageWrapper
import com.intellij.platform.ml.embeddings.jvm.wrappers.AbstractEmbeddingsStorageWrapper
import com.intellij.platform.ml.embeddings.jvm.models.LocalEmbeddingServiceProviderImpl

@Service(Service.Level.APP)
class InProcessTextEmbeddingsStorageManager : TextEmbeddingsStorageManager<EntityId> {
  private val modelService: LocalEmbeddingServiceProvider = LocalEmbeddingServiceProviderImpl.getInstance()

  override suspend fun addAbsent(project: Project, indexId: IndexId, entries: List<IndexEntry<EntityId>>) {
    val embeddingService = modelService.getService() ?: return
    val embeddings = embeddingService.embed(entries.map { it.text })
    getWrapper(project, indexId).addEntries(entries.map { it.key }.zip(embeddings))
  }

  override suspend fun remove(project: Project, indexId: IndexId, keys: List<EntityId>) {
    getWrapper(project, indexId).removeEntries(keys)
  }

  override suspend fun search(
    project: Project, indexId: IndexId,
    query: String, limit: Int, similarityThreshold: Float?,
  ): List<ScoredKey<EntityId>> {
    FileBasedEmbeddingIndexer.getInstance().triggerIndexing(project)
    val embeddingService = modelService.getService() ?: return emptyList()
    val queryEmbedding = embeddingService.embed(query)
    return getWrapper(project, indexId)
      .searchNeighbours(queryEmbedding, limit, similarityThreshold?.toDouble())
      .map { (id, similarity) -> ScoredKey(id, similarity) }
  }

  override suspend fun startIndexingSession(project: Project, indexId: IndexId) {
    getWrapper(project, indexId).startIndexingSession()
  }

  override suspend fun finishIndexingSession(project: Project, indexId: IndexId) {
    getWrapper(project, indexId).finishIndexingSession()
  }

  companion object {
    private fun getWrapper(project: Project, indexId: IndexId): AbstractEmbeddingsStorageWrapper {
      return when (indexId) {
        IndexId.CLASSES -> ClassEmbeddingsStorageWrapper.getInstance(project)
        IndexId.FILES -> FileEmbeddingsStorageWrapper.getInstance(project)
        IndexId.SYMBOLS -> SymbolEmbeddingsStorageWrapper.getInstance(project)
        else -> throw IllegalArgumentException("Unsupported index id: $indexId")
      }
    }

    fun getInstance(): InProcessTextEmbeddingsStorageManager = service()
  }
}