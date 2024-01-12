// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.indices

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.platform.ml.embeddings.search.utils.ScoredText

interface EmbeddingSearchIndex {
  val size: Int
  var limit: Int?

  operator fun contains(id: String): Boolean

  fun onIndexingStart()
  fun onIndexingFinish()

  suspend fun addEntries(values: Iterable<Pair<String, FloatTextEmbedding>>, shouldCount: Boolean = false)

  suspend fun saveToDisk()
  suspend fun loadFromDisk()

  fun findClosest(searchEmbedding: FloatTextEmbedding, topK: Int, similarityThreshold: Double? = null): List<ScoredText>
  fun streamFindClose(searchEmbedding: FloatTextEmbedding, similarityThreshold: Double? = null): Sequence<ScoredText>

  fun estimateMemoryUsage(): Long
  fun estimateLimitByMemory(memory: Long): Int
  fun checkCanAddEntry(): Boolean
}

internal fun Map<String, FloatTextEmbedding>.findClosest(searchEmbedding: FloatTextEmbedding,
                                                         topK: Int, similarityThreshold: Double?): List<ScoredText> {
  return asSequence()
    .map { it.key to searchEmbedding.times(it.value) }
    .filter { (_, similarity) -> if (similarityThreshold != null) similarity > similarityThreshold else true }
    .sortedByDescending { (_, similarity) -> similarity }
    .take(topK)
    .map { (id, similarity) -> ScoredText(id, similarity.toDouble()) }
    .toList()
}

internal fun Sequence<Pair<String, FloatTextEmbedding>>.streamFindClose(queryEmbedding: FloatTextEmbedding,
                                                                        similarityThreshold: Double?): Sequence<ScoredText> {
  return map { (id, embedding) -> id to queryEmbedding.times(embedding) }
    .filter { similarityThreshold == null || it.second > similarityThreshold }
    .map { (id, similarity) -> ScoredText(id, similarity.toDouble()) }
}