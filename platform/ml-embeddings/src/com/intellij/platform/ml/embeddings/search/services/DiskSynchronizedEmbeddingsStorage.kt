// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.logging.EmbeddingSearchLogger
import com.intellij.platform.ml.embeddings.search.indices.DiskSynchronizedEmbeddingSearchIndex
import com.intellij.platform.ml.embeddings.search.indices.IndexableEntity
import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.platform.ml.embeddings.services.LocalEmbeddingServiceProvider
import com.intellij.platform.ml.embeddings.utils.generateEmbedding
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
abstract class DiskSynchronizedEmbeddingsStorage<T : IndexableEntity>(val project: Project,
                                                                      private val cs: CoroutineScope) : EmbeddingsStorage {
  abstract val index: DiskSynchronizedEmbeddingSearchIndex

  internal abstract val reportableIndex: EmbeddingSearchLogger.Index

  private val offloadRequest = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val usageSessionCount = AtomicInteger(0)
  private var isIndexLoaded = false

  private val indexLoadingMutex = Mutex()

  init {
    cs.launch {
      offloadRequest.debounce(OFFLOAD_TIMEOUT).collectLatest {
        // Make sure offloading does not happen during the indexing or search
        if (usageSessionCount.get() == 0) offloadIndex() else scheduleOffload()
      }
    }
  }

  suspend fun loadIndex() = indexLoadingMutex.withLock {
    if (!isIndexLoaded) {
      index.loadFromDisk()
      isIndexLoaded = true
      logger.debug { "Loaded index: ${reportableIndex.name}" }
    }
    else {
      logger.debug { "Canceled index loading, already loaded: ${reportableIndex.name}" }
    }
  }

  suspend fun saveIndex() = indexLoadingMutex.withLock { index.saveToDisk() }

  private suspend fun offloadIndex() = indexLoadingMutex.withLock {
    if (isIndexLoaded) {
      index.saveToDisk()
      index.offload()
      isIndexLoaded = false
      logger.debug { "Offloaded index: ${reportableIndex.name}" }
    }
    else {
      logger.debug { "Canceled index offloading, not loaded yet: ${reportableIndex.name}" }
    }
  }

  suspend fun startIndexingSession() {
    usageSessionCount.incrementAndGet()
    loadIndex()
  }

  fun finishIndexingSession() {
    scheduleOffload()
    usageSessionCount.decrementAndGet()
  }

  private fun scheduleOffload() {
    check(offloadRequest.tryEmit(Unit))
  }

  @RequiresBackgroundThread
  override suspend fun searchNeighbours(text: String, topK: Int, similarityThreshold: Double?): List<ScoredText> {
    FileBasedEmbeddingStoragesManager.getInstance(project).triggerIndexing()
    val searchStartTime = System.nanoTime()
    val neighbours: List<ScoredText>
    val loadJob = cs.launch(Dispatchers.IO) { loadIndex() }
    val embedding = generateEmbedding(text) ?: return emptyList()
    LocalEmbeddingServiceProvider.getInstance().scheduleCleanup()
    usageSessionCount.incrementAndGet()
    try {
      loadJob.join()
      neighbours = index.findClosest(embedding, topK, similarityThreshold)
      scheduleOffload()
    }
    finally {
      usageSessionCount.decrementAndGet()
    }
    EmbeddingSearchLogger.searchFinished(project, reportableIndex, TimeoutUtil.getDurationMillis(searchStartTime))
    return neighbours
  }

  @RequiresBackgroundThread
  suspend fun streamSearchNeighbours(text: String, similarityThreshold: Double? = null): Flow<ScoredText> {
    FileBasedEmbeddingStoragesManager.getInstance(project).triggerIndexing()
    val loadJob = cs.launch(Dispatchers.IO) { loadIndex() }
    val embedding = generateEmbedding(text) ?: return emptyFlow()
    LocalEmbeddingServiceProvider.getInstance().scheduleCleanup()
    return flow {
      usageSessionCount.incrementAndGet()
      try {
        loadJob.join()
        emitAll(index.streamFindClose(embedding, similarityThreshold))
        scheduleOffload()
      }
      finally {
        usageSessionCount.decrementAndGet()
      }
    }
  }

  fun registerInMemoryManager() {
    EmbeddingIndexMemoryManager.getInstance().registerIndex(index)
  }

  companion object {
    private val OFFLOAD_TIMEOUT = 10.seconds
    private val logger = Logger.getInstance(DiskSynchronizedEmbeddingsStorage::class.java)
  }
}
