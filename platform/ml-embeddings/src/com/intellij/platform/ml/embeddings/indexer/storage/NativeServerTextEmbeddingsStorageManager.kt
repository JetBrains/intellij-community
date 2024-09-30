// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.storage

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.platform.ml.embeddings.actions.ActionEmbeddingStorageManager
import com.intellij.platform.ml.embeddings.external.client.EmbeddingsStorageLocation
import com.intellij.platform.ml.embeddings.indexer.IndexId
import com.intellij.platform.ml.embeddings.external.client.NativeServerManager
import com.intellij.platform.ml.embeddings.indexer.FileBasedEmbeddingIndexer
import org.jetbrains.embeddings.local.server.stubs.clearRequest
import org.jetbrains.embeddings.local.server.stubs.finishRequest
import org.jetbrains.embeddings.local.server.stubs.indexEntity
import org.jetbrains.embeddings.local.server.stubs.presentRequest
import org.jetbrains.embeddings.local.server.stubs.removeRequest
import org.jetbrains.embeddings.local.server.stubs.searchRequest
import org.jetbrains.embeddings.local.server.stubs.startRequest
import org.jetbrains.embeddings.local.server.stubs.statsRequest
import org.jetbrains.embeddings.local.server.stubs.storageLocation

@Service(Service.Level.APP)
class NativeServerTextEmbeddingsStorageManager : TextEmbeddingsStorageManager<Long> {
  override suspend fun addAbsent(project: Project?, indexId: IndexId, entries: List<IndexEntry<Long>>) {
    NativeServerManager.getInstance().getConnection().ensureVectorsPresent(presentRequest {
      location = getStorageLocation(project, indexId)
      entities.addAll(entries.map { (key, value) ->
        indexEntity {
          id = key
          text = value
        }
      })
    })
  }

  override suspend fun remove(project: Project?, indexId: IndexId, keys: List<Long>) {
    NativeServerManager.getInstance().getConnection().removeVectors(removeRequest {
      location = getStorageLocation(project, indexId)
      ids.addAll(keys)
    })
  }

  override suspend fun clearStorage(project: Project?, indexId: IndexId) {
    NativeServerManager.getInstance().getConnection().clearStorage(clearRequest {
      location = getStorageLocation(project, indexId)
    })
  }

  override suspend fun search(
    project: Project?,
    indexId: IndexId,
    query: String,
    limit: Int,
    similarityThreshold: Float?,
  ): List<ScoredKey<Long>> {
    if (indexId == IndexId.ACTIONS) ActionEmbeddingStorageManager.getInstance().triggerIndexing()
    else FileBasedEmbeddingIndexer.getInstance().triggerIndexing(project!!)
    val connection = NativeServerManager.getInstance().getConnection()
    val request = searchRequest {
      location = getStorageLocation(project, indexId)
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
  override suspend fun startIndexingSession(project: Project?, indexId: IndexId) {
    NativeServerManager.getInstance().getConnection().startIndexingSession(startRequest {
      location = getStorageLocation(project, indexId)
    })
  }

  @Deprecated("Should not be needed when all file changes are synchronized with storage")
  override suspend fun finishIndexingSession(project: Project?, indexId: IndexId) {
    NativeServerManager.getInstance().getConnection().finishIndexingSession(finishRequest {
      location = getStorageLocation(project, indexId)
    })
  }

  override suspend fun getStorageStats(project: Project?, indexId: IndexId): StorageStats {
    val stats = NativeServerManager.getInstance().getConnection().getStorageStats(statsRequest {
      location = getStorageLocation(project, indexId)
    })
    return StorageStats(stats.size, stats.bytes)
  }

  override fun getBatchSize(): Int = 32 // Empirically selected best value

  companion object {
    fun getProjectId(project: Project): String = project.getProjectCacheFileName()

    fun getInstance(): NativeServerTextEmbeddingsStorageManager = service()

    fun getStorageLocation(project: Project?, indexId: IndexId): EmbeddingsStorageLocation {
      return storageLocation {
        project?.let { path.add(getProjectId(it)) }
        path.add(indexId.toString())
      }
    }
  }
}