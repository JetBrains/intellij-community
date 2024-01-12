// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.indices

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.platform.ml.embeddings.search.utils.LockedSequenceWrapper
import com.intellij.util.containers.CollectionFactory
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Concurrent [EmbeddingSearchIndex] that stores all embeddings in the memory and allows
 * simultaneous read operations from multiple consumers.
 * Can be persisted to disk.
 */
class InMemoryEmbeddingSearchIndex(root: Path, limit: Int? = null) : EmbeddingSearchIndex {
  private var idToEmbedding: MutableMap<String, FloatTextEmbedding> = CollectionFactory.createSmallMemoryFootprintMap()
  private val uncheckedIds: MutableSet<String> = ConcurrentCollectionFactory.createConcurrentSet()
  private val lock = ReentrantReadWriteLock()

  private val fileManager = LocalEmbeddingIndexFileManager(root)

  override var limit = limit
    set(value) = lock.write {
      // Shrink index if necessary:
      if (value != null && value < idToEmbedding.size) {
        idToEmbedding = idToEmbedding.toList().take(value).toMap().toMutableMap()
      }
      field = value
    }

  override val size: Int get() = lock.read { idToEmbedding.size }

  override operator fun contains(id: String): Boolean = lock.read {
    uncheckedIds.remove(id)
    id in idToEmbedding
  }

  override fun onIndexingStart() {
    uncheckedIds.clear()
    uncheckedIds.addAll(idToEmbedding.keys)
  }

  override fun onIndexingFinish() = lock.write {
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
    val (ids, embeddings) = fileManager.loadIndex() ?: return
    idToEmbedding = (ids zip embeddings).toMap().toMutableMap()
  }

  override fun findClosest(searchEmbedding: FloatTextEmbedding, topK: Int, similarityThreshold: Double?): List<ScoredText> = lock.read {
    return idToEmbedding.findClosest(searchEmbedding, topK, similarityThreshold)
  }

  override fun streamFindClose(searchEmbedding: FloatTextEmbedding, similarityThreshold: Double?): Sequence<ScoredText> {
    return LockedSequenceWrapper(lock::readLock) {
      this.idToEmbedding // manually use the receiver here to make sure the property is not captured by reference
        .asSequence()
        .map { it.key to it.value }
        .streamFindClose(searchEmbedding, similarityThreshold)
    }
  }

  override fun estimateMemoryUsage() = fileManager.embeddingSizeInBytes.toLong() * size

  override fun estimateLimitByMemory(memory: Long): Int {
    return (memory / fileManager.embeddingSizeInBytes).toInt()
  }

  override fun checkCanAddEntry(): Boolean = lock.read {
    return limit == null || idToEmbedding.size < limit!!
  }

  private suspend fun save() {
    val (ids, embeddings) = idToEmbedding.toList().unzip()
    fileManager.saveIndex(ids, embeddings)
  }
}