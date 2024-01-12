// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.indices

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.platform.ml.embeddings.search.utils.LockedSequenceWrapper
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Concurrent [EmbeddingSearchIndex] that synchronizes all index change operations with disk and
 * allows simultaneous read operations from multiple consumers.
 * Incremental operations do not rewrite the whole storage file with embeddings.
 * Instead, they change only the corresponding sections in the file.
 */
class DiskSynchronizedEmbeddingSearchIndex(val root: Path, limit: Int? = null) : EmbeddingSearchIndex {
  private var indexToId: MutableMap<Int, String> = CollectionFactory.createSmallMemoryFootprintMap()
  private var idToEntry: MutableMap<String, IndexEntry> = CollectionFactory.createSmallMemoryFootprintMap()
  private val uncheckedIds: MutableSet<String> = ConcurrentCollectionFactory.createConcurrentSet()

  private val lock = ReentrantReadWriteLock()

  private val fileManager = LocalEmbeddingIndexFileManager(root)

  override var limit = limit
    set(value) = lock.write {
      if (value != null) {
        // Shrink index if necessary:
        while (idToEntry.size > value) {
          delete(indexToId[idToEntry.size - 1]!!, all = true, shouldSaveIds = false)
        }
        saveIds()
      }
      field = value
    }

  internal data class IndexEntry(
    var index: Int,
    var count: Int,
    val embedding: FloatTextEmbedding
  )

  override val size: Int get() = lock.read { idToEntry.size }

  override operator fun contains(id: String): Boolean = lock.read {
    uncheckedIds.remove(id)
    id in idToEntry
  }

  override fun onIndexingStart() {
    uncheckedIds.clear()
    uncheckedIds.addAll(idToEntry.keys)
  }

  override fun onIndexingFinish() = lock.write {
    uncheckedIds.forEach { delete(it, all = true, shouldSaveIds = false) }
    uncheckedIds.clear()
  }

  override suspend fun addEntries(values: Iterable<Pair<String, FloatTextEmbedding>>,
                                  shouldCount: Boolean) = coroutineScope {
    lock.write {
      for ((id, embedding) in values) {
        ensureActive()
        val entry = idToEntry.getOrPut(id) {
          if (limit != null && idToEntry.size >= limit!!) return@write
          val index = idToEntry.size
          indexToId[index] = id
          IndexEntry(index, 0, embedding)
        }
        if (shouldCount || entry.count == 0) {
          entry.count += 1
        }
      }
    }
  }

  override suspend fun saveToDisk() = lock.read { save() }

  override suspend fun loadFromDisk() = coroutineScope {
    val (ids, embeddings) = fileManager.loadIndex() ?: return@coroutineScope
    val idToIndex = ids.withIndex().associate { it.value to it.index }
    val idToEmbedding = (ids zip embeddings).toMap()
    ensureActive()
    lock.write {
      ensureActive()
      indexToId = CollectionFactory.createSmallMemoryFootprintMap(ids.withIndex().associate { it.index to it.value })
      idToEntry = CollectionFactory.createSmallMemoryFootprintMap(
        ids.associateWith { IndexEntry(idToIndex[it]!!, 0, idToEmbedding[it]!!) }
      )
    }
  }

  override fun findClosest(searchEmbedding: FloatTextEmbedding, topK: Int, similarityThreshold: Double?): List<ScoredText> = lock.read {
    return idToEntry.mapValues { it.value.embedding }.findClosest(searchEmbedding, topK, similarityThreshold)
  }

  override fun streamFindClose(searchEmbedding: FloatTextEmbedding, similarityThreshold: Double?): Sequence<ScoredText> {
    return LockedSequenceWrapper(lock::readLock) {
      this.idToEntry // manually use the receiver here to make sure the property is not captured by reference
        .asSequence()
        .map { it.key to it.value.embedding }
        .streamFindClose(searchEmbedding, similarityThreshold)
    }
  }

  override fun estimateMemoryUsage() = fileManager.embeddingSizeInBytes.toLong() * size

  override fun estimateLimitByMemory(memory: Long): Int {
    return (memory / fileManager.embeddingSizeInBytes).toInt()
  }

  override fun checkCanAddEntry(): Boolean = lock.read {
    return limit == null || idToEntry.size < limit!!
  }

  private suspend fun save() = coroutineScope {
    val ids = idToEntry.toList().sortedBy { it.second.index }.map { it.first }
    val embeddings = ids.map { idToEntry[it]!!.embedding }
    fileManager.saveIndex(ids, embeddings)
  }

  fun deleteEntry(id: String) = lock.write {
    delete(id)
  }

  fun addEntry(id: String, embedding: FloatTextEmbedding) = lock.write {
    add(id, embedding)
  }

  /* Optimization for consequent delete and add operations */
  fun updateEntry(id: String, newId: String, embedding: FloatTextEmbedding) = lock.write {
    if (id !in idToEntry) return
    if (idToEntry[id]!!.count == 1 && newId !in this) {
      val index = idToEntry[id]!!.index
      fileManager[index] = embedding

      idToEntry.remove(id)
      idToEntry[newId] = IndexEntry(index, 1, embedding)
      indexToId[index] = newId

      saveIds()
    }
    else {
      // Do not apply optimization
      delete(id)
      add(newId, embedding)
    }
  }

  private fun add(id: String, embedding: FloatTextEmbedding, shouldCount: Boolean = false) {
    val entry = idToEntry.getOrPut(id) {
      if (limit != null && idToEntry.size >= limit!!) return@add
      val index = idToEntry.size
      fileManager[index] = embedding
      indexToId[index] = id
      IndexEntry(index, 0, embedding)
    }
    if (shouldCount || entry.count == 0) {
      entry.count += 1
      if (entry.count == 1) {
        saveIds()
      }
    }
  }

  private fun delete(id: String, all: Boolean = false, shouldSaveIds: Boolean = true) {
    val entry = idToEntry[id] ?: return
    entry.count -= 1
    if (!all && entry.count > 0) return

    val lastIndex = idToEntry.size - 1
    val index = entry.index

    val movedId = indexToId[lastIndex]!!

    fileManager.removeAtIndex(index)
    indexToId[index] = movedId
    indexToId.remove(lastIndex)

    idToEntry[movedId]!!.index = index
    idToEntry.remove(id)

    if (shouldSaveIds) saveIds()
  }

  private fun saveIds() {
    fileManager.saveIds(idToEntry.toList().sortedBy { it.second.index }.map { it.first })
  }
}