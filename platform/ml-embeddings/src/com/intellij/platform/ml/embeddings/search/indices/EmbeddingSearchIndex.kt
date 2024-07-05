// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.indices

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import java.util.PriorityQueue

interface EmbeddingSearchIndex {
  var limit: Int?

  suspend fun getSize(): Int
  suspend fun setLimit(value: Int?)

  suspend fun contains(id: EntityId): Boolean
  suspend fun lookup(id: EntityId): FloatTextEmbedding?
  suspend fun clear()
  suspend fun clearBySourceType(sourceType: EntitySourceType) = Unit

  suspend fun onIndexingStart()
  suspend fun onIndexingFinish()

  suspend fun addEntries(values: Iterable<Pair<EntityId, FloatTextEmbedding>>, shouldCount: Boolean = false)

  suspend fun saveToDisk()
  suspend fun loadFromDisk()
  suspend fun offload()

  suspend fun findClosest(searchEmbedding: FloatTextEmbedding, topK: Int, similarityThreshold: Double? = null): List<ScoredText>
  suspend fun streamFindClose(searchEmbedding: FloatTextEmbedding, similarityThreshold: Double? = null): Flow<ScoredText>

  suspend fun estimateMemoryUsage(): Long
  fun estimateLimitByMemory(memory: Long): Int
  suspend fun checkCanAddEntry(): Boolean
}

internal suspend fun Sequence<Pair<EntityId, FloatTextEmbedding>>.findClosest(
  searchEmbedding: FloatTextEmbedding,
  topK: Int, similarityThreshold: Double?,
): List<ScoredText> = coroutineScope {
  val closest = PriorityQueue<ScoredText>(topK + 1, compareBy { it.similarity })

  map { (id, embedding) -> ScoredText(id.id, searchEmbedding.times(embedding).toDouble()) }
    .filter { similarityThreshold == null || it.similarity > similarityThreshold }
    .forEach {
      ensureActive()
      closest.add(it)
      if (closest.size > topK) closest.poll()
    }

  closest.sortedByDescending { it.similarity }
}

internal fun Sequence<Pair<EntityId, FloatTextEmbedding>>.streamFindClose(
  queryEmbedding: FloatTextEmbedding,
  similarityThreshold: Double?,
): Sequence<ScoredText> {
  return map { (id, embedding) -> ScoredText(id.id, queryEmbedding.times(embedding).toDouble()) }
    .filter { similarityThreshold == null || it.similarity > similarityThreshold }
}