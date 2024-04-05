// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.indices

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.platform.ml.embeddings.search.utils.SuspendingReadWriteLock
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Path

/**
 * Concurrent [EmbeddingSearchIndex] that stores all embeddings in the memory and allows
 * simultaneous read operations from multiple consumers.
 * Can be persisted to disk.
 */
class InMemoryEmbeddingSearchIndex(root: Path, override var limit: Int? = null) : EmbeddingSearchIndex {
  private var idToEmbedding: MutableMap<String, FloatTextEmbedding> = CollectionFactory.createSmallMemoryFootprintMap()
  private val uncheckedIds: MutableSet<String> = ConcurrentCollectionFactory.createConcurrentSet()
  private val lock = SuspendingReadWriteLock()

  private val fileManager = LocalEmbeddingIndexFileManager(root)

  override suspend fun getSize() = lock.read { idToEmbedding.size }

  override suspend fun setLimit(value: Int?) = lock.write {
    // Shrink index if necessary:
    if (value != null && value < idToEmbedding.size) {
      idToEmbedding = idToEmbedding.toList().take(value).toMap().toMutableMap()
    }
    limit = value
  }

  override suspend fun contains(id: String): Boolean = lock.read {
    uncheckedIds.remove(id)
    id in idToEmbedding
  }

  override suspend fun lookup(id: String): FloatTextEmbedding? = lock.read { idToEmbedding[id] }

  override suspend fun clear() = lock.write {
    idToEmbedding.clear()
    uncheckedIds.clear()
  }

  override suspend fun onIndexingStart() {
    uncheckedIds.clear()
    uncheckedIds.addAll(idToEmbedding.keys)
  }

  override suspend fun onIndexingFinish() = lock.write {
    uncheckedIds.forEach { idToEmbedding.remove(it) }
    uncheckedIds.clear()
  }

  override suspend fun addEntries(values: Iterable<Pair<String, FloatTextEmbedding>>,
                                  shouldCount: Boolean) = lock.write {
    if (limit != null) {
      val list = values.toList()
      idToEmbedding.putAll(list.take(minOf(limit!! - idToEmbedding.size, list.size)))
    }
    else {
      idToEmbedding.putAll(values)
    }
  }

  override suspend fun saveToDisk() = lock.read { save() }

  override suspend fun loadFromDisk() = lock.write {
    val (ids, embeddings) = fileManager.loadIndex() ?: return@write
    idToEmbedding = (ids zip embeddings).toMap().toMutableMap()
  }

  override suspend fun findClosest(searchEmbedding: FloatTextEmbedding, topK: Int, similarityThreshold: Double?): List<ScoredText> = lock.read {
    idToEmbedding.findClosest(searchEmbedding, topK, similarityThreshold)
  }

  override suspend fun streamFindClose(searchEmbedding: FloatTextEmbedding, similarityThreshold: Double?): Flow<ScoredText> {
    return flow {
      lock.read {
        idToEmbedding // manually use the receiver here to make sure the property is not captured by reference
          .asSequence()
          .map { it.key to it.value }
          .streamFindClose(searchEmbedding, similarityThreshold)
          .forEach { emit(it) }
      }
    }
  }

  override suspend fun estimateMemoryUsage() = fileManager.embeddingSizeInBytes.toLong() * getSize()

  override fun estimateLimitByMemory(memory: Long): Int {
    return (memory / fileManager.embeddingSizeInBytes).toInt()
  }

  override suspend fun checkCanAddEntry(): Boolean = lock.read {
    limit == null || idToEmbedding.size < limit!!
  }

  private suspend fun save() {
    val (ids, embeddings) = idToEmbedding.toList().unzip()
    fileManager.saveIndex(ids, embeddings)
  }
}