// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ml.embeddings.search.indices.EmbeddingSearchIndex
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Service that tracks the memory usage of semantic indices.
 * Restricted indices should be registered in this manager by calling [registerIndex] on index startup in smart mode.
 *
 * By default, two rules limit the size of each index:
 * - number of indexable entities is not greater than the strong limit defined in registry on a per-index basis
 * - total memory consumption by semantic indices should not exceed one fourth of total JVM free memory in the IDE smart mode
 * In the latter case, each index has a weighted share of the total allowed space, which is defined by `weight` argument in [registerIndex].
 */
@Service(Service.Level.APP)
class EmbeddingIndexMemoryManager {
  private val trackedIndices = mutableListOf<IndexMemoryInfo>()
  private val freeMemoryWithoutIndices by lazy { // expected not to change despite index updates
    Runtime.getRuntime().freeMemory() + estimateTotalEmbeddingsMemoryUsage()
  }
  private val mutex = ReentrantLock()

  // Should be run in smart mode
  fun registerIndex(index: EmbeddingSearchIndex, weight: Int, strongLimit: Int? = null) = mutex.withLock {
    if (trackedIndices.any { it.index === index } || !shouldRestrictMemoryUsage()) return
    trackedIndices.add(IndexMemoryInfo(index, weight, strongLimit))
    val totalWeight = trackedIndices.sumOf { it.weight }
    trackedIndices.forEach {
      val estimatedLimit = it.index.estimateLimitByMemory(totalMemoryLimitForEmbeddings() * it.weight / totalWeight)
      it.index.limit = if (it.strongLimit != null) minOf(estimatedLimit, it.strongLimit) else estimatedLimit
    }
    logger.debug { "Registered index in memory manager, weight: ${weight}, strong limit: ${strongLimit}" }
  }

  private fun shouldRestrictMemoryUsage() = Registry.`is`("search.everywhere.ml.semantic.indexing.restrict.memory.usage")

  private fun totalMemoryLimitForEmbeddings() = freeMemoryWithoutIndices / 4

  private fun estimateTotalEmbeddingsMemoryUsage(): Long = trackedIndices.sumOf { it.index.estimateMemoryUsage() }

  data class IndexMemoryInfo(val index: EmbeddingSearchIndex, val weight: Int, val strongLimit: Int?)

  companion object {
    private val logger = Logger.getInstance(EmbeddingIndexMemoryManager::class.java)

    fun getInstance(): EmbeddingIndexMemoryManager = service()
  }
}