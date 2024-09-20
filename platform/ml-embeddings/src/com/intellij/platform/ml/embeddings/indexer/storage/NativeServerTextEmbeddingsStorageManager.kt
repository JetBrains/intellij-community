// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.storage

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.platform.ml.embeddings.indexer.IndexId
import com.intellij.platform.ml.embeddings.external.client.NativeServerManager
import com.intellij.platform.ml.embeddings.indexer.FileBasedEmbeddingIndexer
import org.jetbrains.embeddings.local.server.stubs.indexEntity
import org.jetbrains.embeddings.local.server.stubs.presentRequest
import org.jetbrains.embeddings.local.server.stubs.removeRequest
import org.jetbrains.embeddings.local.server.stubs.searchRequest

// TODO: move to UUID key type
@Service(Service.Level.APP)
class NativeServerTextEmbeddingsStorageManager : TextEmbeddingsStorageManager<Int> {
  override suspend fun addAbsent(project: Project, indexId: IndexId, entries: List<IndexEntry<Int>>) {
    val request = presentRequest {
      projectId = getProjectId(project)
      indexType = indexId.toString()
      entities.addAll(entries.map { (key, value) ->
        indexEntity {
          id = key
          text = value
        }
      })
    }
    NativeServerManager.getInstance().getConnection().ensureVectorsPresent(request)
  }

  override suspend fun remove(project: Project, indexId: IndexId, keys: List<Int>) {
    NativeServerManager.getInstance().getConnection().removeVectors(removeRequest {
      projectId = getProjectId(project)
      indexType = indexId.toString()
      ids.addAll(keys)
    })
  }

  override suspend fun search(
    project: Project,
    indexId: IndexId,
    query: String,
    limit: Int,
    similarityThreshold: Float?,
  ): List<ScoredKey<Int>> {
    FileBasedEmbeddingIndexer.getInstance().triggerIndexing(project)
    val connection = NativeServerManager.getInstance().getConnection()
    val request = searchRequest {
      projectId = getProjectId(project)
      indexType = indexId.toString()
      text = query
      top = limit
    }
    return connection.search(request).resultsList.mapNotNull {
      val similarity = 1.0f - it.distance
      if (similarityThreshold != null && similarity > similarityThreshold)
        ScoredKey(it.id, similarity)
      else null
    }
  }

  @Deprecated("Should not be needed when all file changes are synchronized with storage")
  override suspend fun startIndexingSession(project: Project, indexId: IndexId) {
    // TODO: implement
  }

  @Deprecated("Should not be needed when all file changes are synchronized with storage")
  override suspend fun finishIndexingSession(project: Project, indexId: IndexId) {
    // TODO: implement
  }

  companion object {
    fun getProjectId(project: Project): String = project.getProjectCacheFileName()

    fun getInstance(): NativeServerTextEmbeddingsStorageManager = service()
  }
}