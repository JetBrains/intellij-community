// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.indices

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.platform.ml.embeddings.search.utils.SuspendingReadWriteLock
import com.intellij.util.containers.CollectionFactory
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Path

/**
 * Concurrent [EmbeddingSearchIndex] that synchronizes all index change operations with disk and
 * allows simultaneous read operations from multiple consumers.
 * Incremental operations do not rewrite the whole storage file with embeddings.
 * Instead, they change only the corresponding sections in the file.
 */
class DiskSynchronizedEmbeddingSearchIndex(val root: Path, override var limit: Int? = null) : EmbeddingSearchIndex {
  private var indexToId: MutableMap<Int, String> = CollectionFactory.createSmallMemoryFootprintMap()
  private var idToEntry: MutableMap<String, IndexEntry> = CollectionFactory.createSmallMemoryFootprintMap()
  private val uncheckedIds: MutableSet<String> = ConcurrentCollectionFactory.createConcurrentSet()
  var changed: Boolean = false

  private val lock = SuspendingReadWriteLock()

  private val fileManager = LocalEmbeddingIndexFileManager(root)

  override suspend fun setLimit(value: Int?) = lock.write {
    if (value != null) {
      // Shrink index if necessary:
      while (idToEntry.size > value) {
        delete(indexToId[idToEntry.size - 1]!!, all = true, shouldSaveIds = false)
      }
      saveIds()
    }
    limit = value
  }

  internal data class IndexEntry(
    var index: Int,
    var count: Int,
    val embedding: FloatTextEmbedding
  )

  override suspend fun getSize() = lock.read { idToEntry.size }

  override suspend fun contains(id: String): Boolean = lock.read {
    uncheckedIds.remove(id)
    id in idToEntry
  }

  override suspend fun lookup(id: String): FloatTextEmbedding? = lock.read { idToEntry[id]?.embedding }

  override suspend fun clear() = lock.write {
    indexToId.clear()
    idToEntry.clear()
    uncheckedIds.clear()
    changed = false
  }

  override suspend fun onIndexingStart() {
    uncheckedIds.clear()
    uncheckedIds.addAll(idToEntry.keys)
  }

  override suspend fun onIndexingFinish() = lock.write {
    if (uncheckedIds.size > 0) changed = true
    uncheckedIds.forEach {
      delete(it, all = true, shouldSaveIds = false)
    }
    uncheckedIds.clear()
  }

  override suspend fun addEntries(values: Iterable<Pair<String, FloatTextEmbedding>>,
                                  shouldCount: Boolean) = lock.write {
    for ((id, embedding) in values) {
      ensureActive()
      val entry = idToEntry.getOrPut(id) {
        changed = true
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

  override suspend fun saveToDisk() = lock.read { save() }

  override suspend fun loadFromDisk() {
    val (ids, embeddings) = fileManager.loadIndex() ?: return
    val idToIndex = ids.withIndex().associate { it.value to it.index }
    val idToEmbedding = (ids zip embeddings).toMap()
    lock.write {
      indexToId = CollectionFactory.createSmallMemoryFootprintMap(ids.withIndex().associate { it.index to it.value })
      idToEntry = CollectionFactory.createSmallMemoryFootprintMap(
        ids.associateWith { IndexEntry(idToIndex[it]!!, 0, idToEmbedding[it]!!) }
      )
    }
  }

  override suspend fun findClosest(searchEmbedding: FloatTextEmbedding, topK: Int, similarityThreshold: Double?): List<ScoredText> = lock.read {
    return@read idToEntry.mapValues { it.value.embedding }.findClosest(searchEmbedding, topK, similarityThreshold)
  }

  override suspend fun streamFindClose(searchEmbedding: FloatTextEmbedding, similarityThreshold: Double?): Flow<ScoredText> {
    return flow {
      lock.read {
        this@DiskSynchronizedEmbeddingSearchIndex.idToEntry // manually use the receiver here to make sure the property is not captured by reference
          .asSequence()
          .map { it.key to it.value.embedding }
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
    limit == null || idToEntry.size < limit!!
  }

  private suspend fun save() {
    val ids = idToEntry.toList().sortedBy { it.second.index }.map { it.first }
    val embeddings = ids.map { idToEntry[it]!!.embedding }
    fileManager.saveIndex(ids, embeddings)
  }

  suspend fun deleteEntry(id: String) = lock.write {
    delete(id)
  }

  suspend fun addEntry(id: String, embedding: FloatTextEmbedding) = lock.write {
    add(id, embedding)
  }

  /* Optimization for consequent delete and add operations */
  suspend fun updateEntry(id: String, newId: String, embedding: FloatTextEmbedding) = lock.write {
    if (id !in idToEntry) return@write
    if (idToEntry[id]!!.count == 1 && newId !in idToEntry) {
      val index = idToEntry[id]!!.index
      fileManager.set(index, embedding)

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

  private suspend fun add(id: String, embedding: FloatTextEmbedding, shouldCount: Boolean = false) {
    val entry = idToEntry.getOrPut(id) {
      changed = true
      if (limit != null && idToEntry.size >= limit!!) return@add
      val index = idToEntry.size
      fileManager.set(index, embedding)
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

  private suspend fun delete(id: String, all: Boolean = false, shouldSaveIds: Boolean = true) {
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

  private suspend fun saveIds() {
    fileManager.saveIds(idToEntry.toList().sortedBy { it.second.index }.map { it.first })
  }
}