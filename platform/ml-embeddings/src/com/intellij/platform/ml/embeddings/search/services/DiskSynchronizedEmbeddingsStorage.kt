// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ml.embeddings.search.indices.DiskSynchronizedEmbeddingSearchIndex
import com.intellij.platform.ml.embeddings.search.indices.IndexableEntity
import com.intellij.platform.ml.embeddings.search.listeners.SemanticIndexingFinishListener
import com.intellij.platform.ml.embeddings.search.utils.SEMANTIC_SEARCH_TRACER
import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager
import com.intellij.platform.ml.embeddings.utils.generateEmbedding
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

abstract class DiskSynchronizedEmbeddingsStorage<T : IndexableEntity>(val project: Project,
                                                                      private val cs: CoroutineScope) : EmbeddingsStorage {
  abstract val index: DiskSynchronizedEmbeddingSearchIndex

  private val indexSetupJob = AtomicReference<Job>(null)

  abstract val scanningTitle: String
  abstract val setupTitle: String
  abstract val spanIndexName: String

  abstract val indexMemoryWeight: Int
  open val indexStrongLimit: Int? = null

  abstract suspend fun getIndexableEntities(files: Iterable<VirtualFile>? = null): ScanResult<T>

  fun prepareForSearch() = cs.launch {
    project.waitForSmartMode() // project may become dumb again, but we don't interfere initial indexing
    withContext(Dispatchers.IO) {
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        launch {
          LocalArtifactsManager.getInstance().downloadArtifactsIfNecessary(project, retryIfCanceled = false)
        }
      }

      launch {
        index.loadFromDisk()
      }
    }

    logger.debug { "Loaded embedding index from disk, size: ${index.size}, root: ${index.root}" }
    generateEmbeddingsIfNecessary()
    SemanticSearchFileChangeListener.getInstance(project).changeEntityTracking(this@DiskSynchronizedEmbeddingsStorage, true)
    project.messageBus.syncPublisher(SemanticIndexingFinishListener.FINISHED).finished()
  }

  fun registerIndexInMemoryManager() = cs.launch {
    project.waitForSmartMode()
    EmbeddingIndexMemoryManager.getInstance().registerIndex(index, indexMemoryWeight, indexStrongLimit)
  }

  fun tryStopGeneratingEmbeddings() {
    SemanticSearchFileChangeListener.getInstance(project).changeEntityTracking(this, false)
    indexSetupJob.getAndSet(null)?.cancel()
  }

  @RequiresBackgroundThread
  override suspend fun searchNeighbours(text: String, topK: Int, similarityThreshold: Double?): List<ScoredText> {
    if (index.size == 0) return emptyList()
    val embedding = generateEmbedding(text) ?: return emptyList()
    return index.findClosest(embedding, topK, similarityThreshold)
  }

  @RequiresBackgroundThread
  suspend fun streamSearchNeighbours(text: String, similarityThreshold: Double? = null): Sequence<ScoredText> {
    if (index.size == 0) return emptySequence()
    val embedding = generateEmbedding(text) ?: return emptySequence()
    return index.streamFindClose(embedding, similarityThreshold)
  }

  @RequiresBackgroundThread
  suspend fun generateEmbeddingsIfNecessary(files: Iterable<VirtualFile>? = null) = coroutineScope {
    logger.debug { "Started indexing for ${this@DiskSynchronizedEmbeddingsStorage.javaClass.simpleName}" }
    launch {
      if (files != null) indexSetupJob.get()?.join() // wait for the previous indexing to complete
      val storageSetupTask = DiskSynchronizedEmbeddingsStorageSetup(index, indexSetupJob, getIndexableEntities(files), files == null)
      SEMANTIC_SEARCH_TRACER.spanBuilder(spanIndexName + "Indexing").useWithScope {
        try {
          if (Registry.`is`("search.everywhere.ml.semantic.indexing.show.progress")) {
            withBackgroundProgress(project, setupTitle) {
              storageSetupTask.run()
            }
          }
          else {
            storageSetupTask.run()
          }
        }
        catch (e: CancellationException) {
          logger.debug { "${this.javaClass.simpleName} indexing was cancelled" }
          throw e
        }
        finally {
          storageSetupTask.onFinish(cs)
        }
        logger.debug { "Finished indexing for ${this@DiskSynchronizedEmbeddingsStorage.javaClass.simpleName}" }
      }
    }
  }

  companion object {
    private val logger = Logger.getInstance(DiskSynchronizedEmbeddingsStorage::class.java)
  }
}

data class ScanResult<T : IndexableEntity>(
  val flow: Flow<Flow<T>>,
  val filesCount: AtomicInteger,
  val filesScanFinished: Channel<Unit>
)