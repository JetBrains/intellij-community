// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableAction
import com.intellij.platform.ml.embeddings.logging.EmbeddingSearchLogger
import com.intellij.platform.ml.embeddings.jvm.models.LocalEmbeddingService
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.platform.ml.embeddings.jvm.wrappers.ActionEmbeddingsStorageWrapper
import com.intellij.platform.ml.embeddings.jvm.artifacts.KILocalArtifactsManager
import com.intellij.platform.ml.embeddings.jvm.models.LocalEmbeddingServiceProviderImpl
import com.intellij.platform.ml.embeddings.utils.normalized
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

@Service(Service.Level.APP)
class ActionEmbeddingStorageManager(private val cs: CoroutineScope) {
  private val indexingScope = cs.childScope("Actions embedding indexing scope")
  private var isFirstIndexing = true
  private var shouldSaveToDisk = false
  private val isIndexingTriggered = AtomicBoolean(false)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val indexDispatcher: CoroutineContext = Dispatchers.Default.limitedParallelism(8)

  fun prepareForSearch(project: Project? = null) = cs.launch {
    val reportProject = project ?: blockingContext { ProjectManager.getInstance().openProjects.firstOrNull() }
    isIndexingTriggered.compareAndSet(false, true)
    indexingScope.coroutineContext.cancelChildren()
    withContext(indexingScope.coroutineContext) {
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        reportProject?.waitForSmartMode()
        loadRequirements(reportProject)
      }
      indexActions(reportProject)
    }
  }

  fun triggerIndexing() {
    if (isIndexingTriggered.compareAndSet(false, true)) prepareForSearch()
  }

  private suspend fun indexActions(project: Project?) {
    LocalEmbeddingServiceProviderImpl.getInstance().indexingSession {
      try {
        if (isFirstIndexing) onFirstIndexingStart()

        val actionsIndexingStartTime = System.nanoTime()
        val indexableActions = getIndexableActions()

        // todo we are going to get rid of progresses here
        /*project?.let {
          withBackgroundProgress(it, EmbeddingsBundle.getMessage("ml.embeddings.indices.actions.generation.label")) {
            reportProgress(indexableActions.size) {
              indexAllActions(embeddingService, indexableActions)
            }
          }
        } ?:*/ indexAllActions(this, indexableActions)

        val durationMs = TimeoutUtil.getDurationMillis(actionsIndexingStartTime)
        EmbeddingSearchLogger.indexingFinished(project, forActions = true, durationMs)
      }
      catch (e: CancellationException) {
        LOG.debug("Actions embedding indexing was cancelled")
        throw e
      }
      finally {
        if (isFirstIndexing) {
          onFirstIndexingFinish()
          isFirstIndexing = false
        }
      }
    }
  }

  private suspend fun onFirstIndexingStart() {
    ActionEmbeddingsStorageWrapper.getInstance().index.onIndexingStart()
  }

  private fun onFirstIndexingFinish(): Job = cs.launch {
    ActionEmbeddingsStorageWrapper.getInstance().index.onIndexingFinish()
    if (shouldSaveToDisk) {
      val indexSavingStartTime = System.nanoTime()
      withContext(Dispatchers.IO) {
        ActionEmbeddingsStorageWrapper.getInstance().index.saveToDisk()
      }
      EmbeddingSearchLogger.indexingSaved(null, forActions = true, TimeoutUtil.getDurationMillis(indexSavingStartTime))
    }
  }

  private suspend fun indexAllActions(embeddingService: LocalEmbeddingService, actions: List<IndexableAction>) = coroutineScope {
    val index = ActionEmbeddingsStorageWrapper.getInstance().index
    // squash texts first to avoid multiple indexing requests
    val results = actions
      .groupBy { it.templateText }
      .map { item ->
        async(indexDispatcher) {
          val embedding = embeddingService.embed(item.key).normalized()
          item to embedding
        }
      }

    val data = results.awaitAll()
    val indexed = data.asSequence()
      .flatMap { (actionText, items) ->
        actionText.value.asSequence()
          .map { it.actionId to items }
      }
      .toList()

    index.addEntries(indexed)
  }

  private suspend fun loadRequirements(project: Project?) {
    withContext(Dispatchers.IO) {
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        launch {
          KILocalArtifactsManager.getInstance().downloadArtifactsIfNecessary(project, retryIfCanceled = false)
        }
      }
      val indexLoadingStartTime = System.nanoTime()
      ActionEmbeddingsStorageWrapper.getInstance().index.loadFromDisk()
      EmbeddingSearchLogger.indexingLoaded(project, forActions = true, TimeoutUtil.getDurationMillis(indexLoadingStartTime))
    }
  }

  companion object {
    private val LOG = Logger.getInstance(ActionEmbeddingStorageManager::class.java)

    fun getInstance(): ActionEmbeddingStorageManager = service()

    private fun shouldIndexAction(action: AnAction): Boolean {
      return !(action is ActionGroup && !action.isSearchable) && action.templatePresentation.hasText()
    }

    private suspend fun getIndexableActions(): List<IndexableAction> {
      val actionManager = serviceAsync<ActionManager>() as ActionManagerImpl
      return readAction {
        actionManager.actions(canReturnStub = true)
          .filter { shouldIndexAction(it) }
          .mapNotNull { action ->
            val id = actionManager.getId(action)
            val templateText = action.templateText?.removeSuffix("…") ?: ""
            if (id == null || templateText.isBlank()) null else IndexableAction(EntityId(id), templateText)
          }
          .toList()
      }
    }
  }
}
