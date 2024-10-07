// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.jvm.indices

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.platform.ml.embeddings.jvm.utils.SuspendingReadWriteLock
import com.intellij.platform.ml.embeddings.indexer.storage.ScoredKey
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
  private val idToEmbedding: MutableMap<EntityId, FloatTextEmbedding> = CollectionFactory.createSmallMemoryFootprintMap()
  private val uncheckedIds: MutableSet<EntityId> = ConcurrentCollectionFactory.createConcurrentSet()
  private val lock = SuspendingReadWriteLock()

  private val fileManager = LocalEmbeddingIndexFileManager(root)

  override suspend fun getSize(): Int = lock.read { idToEmbedding.size }

  override suspend fun setLimit(value: Int?) {
    lock.write {
      // Shrink index if necessary:
      if (value != null && value < idToEmbedding.size) {
        val remaining = idToEmbedding.asSequence().take(value).map { it.toPair() }.toList()
        idToEmbedding.clear()
        idToEmbedding.putAll(remaining)
      }
      limit = value
    }
  }

  override suspend fun contains(id: EntityId): Boolean = lock.read {
    id in idToEmbedding
  }

  override suspend fun lookup(id: EntityId): FloatTextEmbedding? = lock.read { idToEmbedding[id] }

  override suspend fun clear() {
    lock.write {
      idToEmbedding.clear()
      uncheckedIds.clear()
    }
  }

  override suspend fun remove(id: EntityId) {
    lock.write {
      idToEmbedding.remove(id)
    }
  }

  override suspend fun onIndexingStart() {
    lock.write {
      uncheckedIds.clear()
      uncheckedIds.addAll(idToEmbedding.keys)
    }
  }

  override suspend fun onIndexingFinish() {
    lock.write {
      uncheckedIds.forEach { idToEmbedding.remove(it) }
      uncheckedIds.clear()
    }
  }

  override suspend fun addEntries(
    values: Iterable<Pair<EntityId, FloatTextEmbedding>>,
    shouldCount: Boolean,
  ) {
    lock.write {
      if (limit != null) {
        val list = values.toList()
        list.forEach { uncheckedIds.remove(it.first) }
        idToEmbedding.putAll(list.take(minOf(limit!! - idToEmbedding.size, list.size)))
      }
      else {
        idToEmbedding.putAll(values)
      }
    }
  }

  override suspend fun saveToDisk() {
    lock.read { save() }
  }

  override suspend fun loadFromDisk() {
    lock.write {
      val (ids, embeddings) = fileManager.loadIndex() ?: return@write
      idToEmbedding.clear()
      idToEmbedding.putAll(ids zip embeddings)
    }
  }

  override suspend fun offload() {
    lock.write { idToEmbedding.clear() }
  }

  override suspend fun findClosest(
    searchEmbedding: FloatTextEmbedding,
    topK: Int, similarityThreshold: Double?,
  ): List<ScoredKey<EntityId>> = lock.read {
    idToEmbedding.asSequence()
      .map { (id, embedding) -> id to embedding }
      .findClosest(searchEmbedding, topK, similarityThreshold)
  }

  override suspend fun streamFindClose(searchEmbedding: FloatTextEmbedding, similarityThreshold: Double?): Flow<ScoredKey<EntityId>> {
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

  override suspend fun estimateMemoryUsage(): Long = fileManager.embeddingSizeInBytes.toLong() * getSize()

  override fun estimateLimitByMemory(memory: Long): Int {
    return (memory / fileManager.embeddingSizeInBytes).toInt()
  }

  override suspend fun checkCanAddEntry(): Boolean = lock.read {
    limit == null || idToEmbedding.size < limit!!
  }

  private suspend fun save() {
    val (ids, embeddings) = idToEmbedding.asSequence().map { (id, embedding) -> id to embedding }.unzip()
    fileManager.saveIndex(ids, embeddings)
  }
}