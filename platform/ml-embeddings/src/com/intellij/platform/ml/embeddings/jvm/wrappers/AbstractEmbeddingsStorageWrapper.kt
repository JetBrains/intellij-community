// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.jvm.wrappers

import ai.grazie.emb.FloatTextEmbedding
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.platform.ml.embeddings.jvm.indices.VanillaEmbeddingSearchIndex
import com.intellij.platform.ml.embeddings.indexer.IndexId
import com.intellij.platform.ml.embeddings.external.artifacts.LocalArtifactsManager.Companion.SEMANTIC_SEARCH_RESOURCES_DIR_NAME
import com.intellij.platform.ml.embeddings.indexer.FileBasedEmbeddingIndexer
import com.intellij.platform.ml.embeddings.jvm.memory.EmbeddingIndexMemoryManager
import com.intellij.platform.ml.embeddings.jvm.indices.IndexPersistedEventsCounter
import com.intellij.platform.ml.embeddings.jvm.models.LocalEmbeddingServiceProviderImpl
import com.intellij.platform.ml.embeddings.indexer.storage.ScoredKey
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.time.Duration.Companion.seconds

abstract class AbstractEmbeddingsStorageWrapper(
  val project: Project,
  val indexId: IndexId,
  private val cs: CoroutineScope,
) : EmbeddingsStorageWrapper {
  val index: VanillaEmbeddingSearchIndex = VanillaEmbeddingSearchIndex(
    Path(PathManager.getSystemPath())
    / SEMANTIC_SEARCH_RESOURCES_DIR_NAME / OLD_API_DIR_NAME / indexId.toString() / project.getProjectCacheFileName()
  )

  private var isIndexLoaded = false
  private val indexLoadingMutex = Mutex()
  private val accessTime: AtomicLong = AtomicLong(Long.MAX_VALUE)

  init {
    cs.launch {
      while (true) {
        delay(OFFLOAD_TIMEOUT)
        if (TimeoutUtil.getDurationMillis(accessTime.get()) > OFFLOAD_TIMEOUT.inWholeMilliseconds) {
          offloadIndex()
        }
      }
    }
  }

  abstract fun isEnabled(): Boolean

  override suspend fun addEntries(values: Iterable<Pair<EntityId, FloatTextEmbedding>>) {
    accessTime.set(System.nanoTime())
    if (isEnabled()) {
      indexLoadingMutex.withLock {
        load()
        index.addEntries(values)
      }
    }
  }

  override suspend fun removeEntries(keys: List<EntityId>) {
    accessTime.set(System.nanoTime())
    if (isEnabled()) {
      indexLoadingMutex.withLock {
        load()
        for (key in keys) {
          index.deleteEntry(key, false)
        }
      }
    }
  }

  override suspend fun startIndexingSession() {
    accessTime.set(System.nanoTime())
    if (isEnabled()) {
      index.onIndexingStart()
    }
  }

  override suspend fun finishIndexingSession() {
    accessTime.set(System.nanoTime())
    if (isEnabled()) {
      index.onIndexingFinish()
    }
  }

  @RequiresBackgroundThread
  override suspend fun searchNeighbours(queryEmbedding: FloatTextEmbedding, topK: Int, similarityThreshold: Double?): List<ScoredKey<EntityId>> {
    accessTime.set(System.nanoTime())
    loadIndex()
    LocalEmbeddingServiceProviderImpl.getInstance().scheduleCleanup()
    return index.findClosest(queryEmbedding, topK, similarityThreshold)
  }

  @RequiresBackgroundThread
  suspend fun streamSearchNeighbours(queryEmbedding: FloatTextEmbedding, similarityThreshold: Double? = null): Flow<ScoredKey<EntityId>> {
    accessTime.set(System.nanoTime())
    FileBasedEmbeddingIndexer.getInstance().triggerIndexing(project)
    val loadJob = cs.launch(Dispatchers.IO) { loadIndex() }
    LocalEmbeddingServiceProviderImpl.getInstance().scheduleCleanup()
    return flow {
      loadJob.join()
      emitAll(index.streamFindClose(queryEmbedding, similarityThreshold))
    }
  }

  override suspend fun getSize(): Int {
    accessTime.set(System.nanoTime())
    loadIndex()
    return index.getSize()
  }

  override suspend fun estimateMemory(): Long {
    accessTime.set(System.nanoTime())
    loadIndex()
    return index.estimateMemoryUsage()
  }

  override suspend fun clear() {
    accessTime.set(System.nanoTime())
    loadIndex()
    indexLoadingMutex.withLock {
      index.clear()
      index.saveToDisk()
      isIndexLoaded = false
    }
  }

  private suspend fun loadIndex() {
    if (isEnabled()) {
      indexLoadingMutex.withLock { load() }
    }
  }

  private suspend fun load() {
    if (!isIndexLoaded) {
      index.loadFromDisk()
      isIndexLoaded = true
      logger.debug { "Loaded index: ${indexId}" }
    }
  }

  private suspend fun offloadIndex() = indexLoadingMutex.withLock {
    if (isIndexLoaded) {
      index.saveToDisk()
      index.offload()
      getIndexPersistedEventsCounter(project)?.let { cs.launch { it.sendPersistedCount(indexId, project) } }
      isIndexLoaded = false
      logger.debug { "Offloaded index: ${indexId}" }
    }
    else {
      logger.debug { "Canceled index offloading, not loaded yet: ${indexId}" }
    }
  }

  private fun getIndexPersistedEventsCounter(project: Project) = IndexPersistedEventsCounter.EP_NAME.getExtensions(project).firstOrNull()

  fun registerInMemoryManager() {
    EmbeddingIndexMemoryManager.getInstance().registerIndex(index)
  }

  companion object {
    private val OFFLOAD_TIMEOUT = 10.seconds
    private val logger = Logger.getInstance(AbstractEmbeddingsStorageWrapper::class.java)

    const val OLD_API_DIR_NAME = "old-api"
  }
}
