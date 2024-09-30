// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.storage

import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.indexer.IndexId

interface TextEmbeddingsStorageManager<KeyT> {
  suspend fun addAbsent(project: Project?, indexId: IndexId, entries: List<IndexEntry<KeyT>>)

  suspend fun remove(project: Project?, indexId: IndexId, keys: List<KeyT>)

  suspend fun search(project: Project?, indexId: IndexId, query: String, limit: Int, similarityThreshold: Float? = null): List<ScoredKey<KeyT>>

  suspend fun clearStorage(project: Project?, indexId: IndexId)

  suspend fun filterMissingIds(project: Project?, indexId: IndexId, keys: List<KeyT>): List<KeyT> = keys

  suspend fun startIndexingSession(project: Project?, indexId: IndexId)

  suspend fun finishIndexingSession(project: Project?, indexId: IndexId)

  suspend fun getStorageStats(project: Project?, indexId: IndexId): StorageStats

  fun getBatchSize(): Int = DEFAULT_BATCH_SIZE

  companion object {
    private const val DEFAULT_BATCH_SIZE = 32
  }
}

data class IndexEntry<KeyT>(val key: KeyT, val text: String)

data class ScoredKey<KeyT>(val key: KeyT, val similarity: Float)

data class StorageStats(val size: Int, val bytes: Long)
