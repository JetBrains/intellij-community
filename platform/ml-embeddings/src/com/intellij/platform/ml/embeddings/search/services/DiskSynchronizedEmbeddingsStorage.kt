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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
abstract class DiskSynchronizedEmbeddingsStorage<T : IndexableEntity>(val project: Project,
                                                                      private val cs: CoroutineScope) : EmbeddingsStorage {
  abstract val index: DiskSynchronizedEmbeddingSearchIndex

  internal abstract val reportableIndex: EmbeddingSearchLogger.Index

  private val offloadRequest = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val indexingSessionCount = AtomicInteger(0)
  private val isIndexLoading = AtomicBoolean(false)
  private var isIndexLoaded = false

  private val indexLoadingMutex = Mutex()

  init {
    cs.launch {
      offloadRequest.debounce(OFFLOAD_TIMEOUT).collectLatest { if (indexingSessionCount.get() == 0) offloadIndex() }
    }
  }

  suspend fun loadIndex() = indexLoadingMutex.withLock {
    if (!isIndexLoaded) {
      index.loadFromDisk()
      isIndexLoaded = true
      logger.debug { "Loaded index: ${reportableIndex.name}" }
    }
  }

  private suspend fun offloadIndex() = indexLoadingMutex.withLock {
    if (isIndexLoaded) {
      index.saveToDisk()
      index.offload()
      isIndexLoaded = false
      logger.debug { "Offloaded index: ${reportableIndex.name}" }
    }
  }

  suspend fun startIndexingSession() {
    if (index.getSize() == 0) loadIndex()
    indexingSessionCount.incrementAndGet()
  }

  fun finishIndexingSession() {
    indexingSessionCount.decrementAndGet()
    scheduleCleanup()
  }

  fun scheduleCleanup() {
    // Make sure offloading does not happen during the indexing
    if (indexingSessionCount.get() == 0) {
      check(offloadRequest.tryEmit(Unit))
    }
  }

  private suspend fun loadIndexIfNecessary() {
    if (index.getSize() == 0 && isIndexLoading.compareAndSet(false, true)) {
      cs.launch(Dispatchers.IO) {
        loadIndex()
        isIndexLoading.set(false)
      }
    }
  }

  @RequiresBackgroundThread
  override suspend fun searchNeighbours(text: String, topK: Int, similarityThreshold: Double?): List<ScoredText> {
    FileBasedEmbeddingStoragesManager.getInstance(project).triggerIndexing()
    loadIndexIfNecessary()
    val searchStartTime = System.nanoTime()
    val embedding = generateEmbedding(text) ?: return emptyList()
    LocalEmbeddingServiceProvider.getInstance().scheduleCleanup()
    val neighbours = index.findClosest(embedding, topK, similarityThreshold)
    scheduleCleanup()
    EmbeddingSearchLogger.searchFinished(project, reportableIndex, TimeoutUtil.getDurationMillis(searchStartTime))
    return neighbours
  }

  @RequiresBackgroundThread
  suspend fun streamSearchNeighbours(text: String, similarityThreshold: Double? = null): Flow<ScoredText> {
    FileBasedEmbeddingStoragesManager.getInstance(project).triggerIndexing()
    loadIndexIfNecessary()
    val embedding = generateEmbedding(text) ?: return emptyFlow()
    LocalEmbeddingServiceProvider.getInstance().scheduleCleanup()
    scheduleCleanup()
    return index.streamFindClose(embedding, similarityThreshold)
  }

  fun registerInMemoryManager() {
    EmbeddingIndexMemoryManager.getInstance().registerIndex(index)
  }

  companion object {
    private val OFFLOAD_TIMEOUT = 10.seconds
    private val logger = Logger.getInstance(DiskSynchronizedEmbeddingsStorage::class.java)
  }
}
