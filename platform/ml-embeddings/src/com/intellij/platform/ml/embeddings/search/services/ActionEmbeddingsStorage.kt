// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ml.embeddings.EmbeddingsBundle
import com.intellij.platform.ml.embeddings.search.indices.InMemoryEmbeddingSearchIndex import com.intellij.platform.ml.embeddings.search.utils.ScoredText
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager.Companion.SEMANTIC_SEARCH_RESOURCES_DIR
import com.intellij.platform.ml.embeddings.utils.generateEmbedding
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe service for semantic actions search.
 * Holds a state with embeddings for each available action and persists it on disk after calculation.
 * Generates the embeddings for actions not present in the loaded state at the IDE startup event if semantic action search is enabled
 */
@Service(Service.Level.APP)
class ActionEmbeddingsStorage(private val cs: CoroutineScope) : EmbeddingsStorage {
  val index = InMemoryEmbeddingSearchIndex(
    File(PathManager.getSystemPath())
      .resolve(SEMANTIC_SEARCH_RESOURCES_DIR)
      .resolve(LocalArtifactsManager.getInstance().getModelVersion())
      .resolve(INDEX_DIR).toPath()
  )

  private val indexSetupJob = AtomicReference<Job>(null)

  private val setupTitle
    get() = EmbeddingsBundle.getMessage("ml.embeddings.indices.actions.generation.label")

  fun prepareForSearch(project: Project) = SemanticSearchCoroutineScope.getScope(project).launch {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      // In unit tests you have to manually download artifacts when needed
      serviceAsync<LocalArtifactsManager>().downloadArtifactsIfNecessary(project, retryIfCanceled = false)
    }
    index.loadFromDisk()
    generateEmbeddingsIfNecessary(project)
  }

  fun tryStopGeneratingEmbeddings() = indexSetupJob.getAndSet(null)?.cancel()

  /* Thread-safe job for updating embeddings. Consequent call stops the previous execution */
  @RequiresBackgroundThread
  suspend fun generateEmbeddingsIfNecessary(project: Project) = coroutineScope {
    val backgroundable = ActionEmbeddingsStorageSetup(index, indexSetupJob)
    try {
      if (Registry.`is`("search.everywhere.ml.semantic.indexing.show.progress")) {
        withBackgroundProgress(project, setupTitle) {
          backgroundable.run()
        }
      }
      else {
        backgroundable.run()
      }
    }
    catch (e: CancellationException) {
      LOG.debug("Actions embedding indexing was cancelled")
      throw e
    }
    finally {
      backgroundable.onFinish(cs)
    }
  }

  @RequiresBackgroundThread
  override suspend fun searchNeighbours(text: String, topK: Int, similarityThreshold: Double?): List<ScoredText> {
    if (index.size == 0) return emptyList()
    val embedding = generateEmbedding(text) ?: return emptyList()
    return index.findClosest(searchEmbedding = embedding, topK = topK, similarityThreshold = similarityThreshold)
  }

  @RequiresBackgroundThread
  suspend fun streamSearchNeighbours(text: String, similarityThreshold: Double? = null): Sequence<ScoredText> {
    if (index.size == 0) return emptySequence()
    val embedding = generateEmbedding(text) ?: return emptySequence()
    return index.streamFindClose(embedding, similarityThreshold)
  }

  companion object {
    private const val INDEX_DIR = "actions"

    private val LOG = logger<ActionEmbeddingsStorage>()

    fun getInstance(): ActionEmbeddingsStorage = service()

    private fun shouldIndexAction(action: AnAction): Boolean {
      return !(action is ActionGroup && !action.isSearchable) && action.templatePresentation.hasText()
    }

    internal fun getIndexableActions(actionManager: ActionManagerImpl): Set<AnAction> {
      return actionManager.actions(canReturnStub = true).filterTo(LinkedHashSet()) { shouldIndexAction(it) }
    }
  }
}