// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.ml.embeddings.indexer.configuration.EmbeddingsConfiguration.Companion.getStorageManagerWrapper
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableEntity
import com.intellij.platform.ml.embeddings.indexer.searcher.EmbeddingEntitiesIndexer
import com.intellij.platform.ml.embeddings.indexer.searcher.index.IndexBasedEmbeddingEntitiesIndexer
import com.intellij.platform.ml.embeddings.indexer.searcher.vfs.VFSBasedEmbeddingEntitiesIndexer
import com.intellij.platform.ml.embeddings.logging.EmbeddingSearchLogger
import com.intellij.platform.ml.embeddings.settings.EmbeddingIndexSettingsImpl
import com.intellij.platform.ml.embeddings.utils.SEMANTIC_SEARCH_TRACER
import com.intellij.platform.ml.embeddings.utils.SemanticSearchCoroutineScope
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val TOTAL_THREAD_LIMIT_FOR_INDEXING = 8

@Service(Service.Level.APP)
class FileBasedEmbeddingIndexer(private val cs: CoroutineScope) : Disposable {
  private val indexerScope = cs.childScope("Embedding indexer scope")
  private val indexedProjects = mutableSetOf<Project>()
  private val indexingJobs = mutableMapOf<Project, Job>()
  private val jobsMutex = Mutex()
  private val entitiesIndexer: EmbeddingEntitiesIndexer = if (Registry.`is`("intellij.platform.ml.embeddings.use.file.based.index"))
    IndexBasedEmbeddingEntitiesIndexer(indexerScope)
  else VFSBasedEmbeddingEntitiesIndexer(indexerScope)

  private val triggerIndexingOnSearch
    get() = Registry.`is`("intellij.platform.ml.embeddings.trigger.indexing.on.search")

  init {
    Disposer.register(this, entitiesIndexer)
  }

  fun prepareForSearch(project: Project): Job = cs.launch {
    Disposer.register(project) {
      runBlockingMaybeCancellable {
        jobsMutex.withLock {
          indexingJobs.remove(project)
          indexedProjects.remove(project)
        }
      }
    }
    val currentJob = jobsMutex.withLock {
      // Cancel previous indexing for this project
      indexingJobs[project]?.cancel()
      // Create a new indexing job for this project
      val job = SemanticSearchCoroutineScope.getScope(project).launch { indexProject(project) }
      indexingJobs[project] = job
      indexedProjects.add(project)
      job
    }
    currentJob.join()
    jobsMutex.withLock {
      indexingJobs.remove(project)
    }
  }

  suspend fun triggerIndexing(project: Project) {
    if (!triggerIndexingOnSearch) return
    var shouldIndex = false
    jobsMutex.withLock {
      if (project !in indexedProjects) {
        indexedProjects.add(project)
        shouldIndex = true
      }
    }
    if (shouldIndex) {
      prepareForSearch(project)
    }
  }

  private suspend fun indexProject(project: Project) {
    project.waitForSmartMode()
    logger.debug { "Started full project embedding indexing" }
    SEMANTIC_SEARCH_TRACER.spanBuilder(INDEXING_SPAN_NAME).useWithScope {
      startIndexingSession(project)
      try {
        val projectIndexingStartTime = System.nanoTime()
        val settings = EmbeddingIndexSettingsImpl.getInstance()
        if (settings.shouldIndexAnythingFileBased) {
          entitiesIndexer.index(project, settings)
        }
        EmbeddingSearchLogger.indexingFinished(project, forActions = false, TimeoutUtil.getDurationMillis(projectIndexingStartTime))
      }
      finally {
        finishIndexingSession(project)
      }
    }
    logger.debug { "Finished full project embedding indexing" }
  }

  private suspend fun startIndexingSession(project: Project) {
    for (indexId in FILE_BASED_INDICES) {
      getStorageManagerWrapper(indexId).startIndexingSession(project)
    }
  }

  private suspend fun finishIndexingSession(project: Project) {
    for (indexId in FILE_BASED_INDICES) {
      getStorageManagerWrapper(indexId).finishIndexingSession(project)
    }
  }

  companion object {
    fun getInstance(): FileBasedEmbeddingIndexer = service()

    internal const val INDEXING_VERSION = "0.0.1"

    val FILE_BASED_INDICES = arrayOf(IndexId.FILES, IndexId.CLASSES, IndexId.SYMBOLS)

    private val logger = Logger.getInstance(FileBasedEmbeddingIndexer::class.java)

    private const val INDEXING_SPAN_NAME = "embeddingIndexing"
  }

  override fun dispose() {}
}

suspend fun addAbsentEntities(project: Project, indexId: IndexId, channel: ReceiveChannel<IndexableEntity>) {
  val wrapper = getStorageManagerWrapper(indexId)
  val entities = ArrayList<IndexableEntity>(wrapper.getBatchSize())
  var index = 0
  for (entity in channel) {
    if (entities.size < wrapper.getBatchSize()) entities.add(entity) else entities[index] = entity
    ++index
    if (index == wrapper.getBatchSize()) {
      wrapper.addAbsent(project, entities)
      index = 0
    }
  }
  if (entities.isNotEmpty()) {
    wrapper.addAbsent(project, entities)
  }
}

internal suspend fun extractAndAddEntities(
  project: Project,
  launchSearching: suspend (Channel<IndexableEntity>?, Channel<IndexableEntity>?, Channel<IndexableEntity>?) -> Unit,
) = coroutineScope {
  val filesChannel = IndexId.FILES.createEntitiesChannel()
  val classesChannel = IndexId.CLASSES.createEntitiesChannel()
  val symbolsChannel = IndexId.SYMBOLS.createEntitiesChannel()

  try {
    if (filesChannel != null) {
      launch { addAbsentEntities(project, IndexId.FILES, filesChannel) }
    }
    if (classesChannel != null) {
      launch { addAbsentEntities(project, IndexId.CLASSES, classesChannel) }
    }
    if (symbolsChannel != null) {
      launch { addAbsentEntities(project, IndexId.SYMBOLS, symbolsChannel) }
    }

    launchSearching(filesChannel, classesChannel, symbolsChannel)
  }
  finally {
    // Here all producer coroutines launch from launchSearching finished,
    // so we can close channels to make consumer coroutines finish
    filesChannel?.close()
    classesChannel?.close()
    symbolsChannel?.close()
  }
}
