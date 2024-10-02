// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.jvm.wrappers

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.platform.ml.embeddings.indexer.storage.ScoredKey
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId

interface EmbeddingsStorageWrapper {
  suspend fun addEntries(values: Iterable<Pair<EntityId, FloatTextEmbedding>>)

  suspend fun removeEntries(keys: List<EntityId>)

  suspend fun searchNeighbours(queryEmbedding: FloatTextEmbedding, limit: Int, similarityThreshold: Double?): List<ScoredKey<EntityId>>

  suspend fun clear()

  suspend fun startIndexingSession()

  suspend fun finishIndexingSession()

  suspend fun getSize(): Int

  suspend fun estimateMemory(): Long
}