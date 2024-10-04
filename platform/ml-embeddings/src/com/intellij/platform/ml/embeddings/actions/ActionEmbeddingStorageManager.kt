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
import com.intellij.platform.ml.embeddings.indexer.IndexId
import com.intellij.platform.ml.embeddings.indexer.configuration.EmbeddingsConfiguration
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableAction
import com.intellij.platform.ml.embeddings.logging.EmbeddingSearchLogger
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.platform.ml.embeddings.jvm.models.LocalEmbeddingServiceProviderImpl
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

@Service(Service.Level.APP)
class ActionEmbeddingStorageManager(private val cs: CoroutineScope) {
  private val indexingScope = cs.childScope("Actions embedding indexing scope")
  private var isFirstIndexing = true
  private val isIndexingTriggered = AtomicBoolean(false)

  fun prepareForSearch(project: Project? = null) = cs.launch {
    val reportProject = project ?: blockingContext { ProjectManager.getInstance().openProjects.firstOrNull() }
    isIndexingTriggered.compareAndSet(false, true)
    indexingScope.coroutineContext.cancelChildren()
    withContext(indexingScope.coroutineContext) {
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        reportProject?.waitForSmartMode()
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
        } ?:*/ indexAllActions(indexableActions)

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
    EmbeddingsConfiguration.getStorageManagerWrapper(IndexId.ACTIONS).startIndexingSession(null)
  }

  private suspend fun onFirstIndexingFinish() {
    EmbeddingsConfiguration.getStorageManagerWrapper(IndexId.ACTIONS).finishIndexingSession(null)
  }

  private suspend fun indexAllActions(actions: List<IndexableAction>) = coroutineScope {
    val storageManagerWrapper = EmbeddingsConfiguration.getStorageManagerWrapper(IndexId.ACTIONS)
    actions.asSequence().chunked(storageManagerWrapper.getBatchSize()).forEach { chunk ->
      storageManagerWrapper.addAbsent(null, chunk)
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
