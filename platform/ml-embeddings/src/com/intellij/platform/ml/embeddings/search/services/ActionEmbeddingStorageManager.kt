// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

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
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ml.embeddings.EmbeddingsBundle
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager
import com.intellij.platform.ml.embeddings.services.LocalEmbeddingServiceProvider
import com.intellij.platform.ml.embeddings.utils.normalized
import com.intellij.platform.util.coroutines.namedChildScope
import com.intellij.platform.util.progress.ProgressReporter
import com.intellij.platform.util.progress.reportProgress
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

@Service(Service.Level.APP)
class ActionEmbeddingStorageManager(private val cs: CoroutineScope) {
  private val indexingScope = cs.namedChildScope("Actions embedding indexing scope")
  private var isFirstIndexing = true
  private var shouldSaveToDisk = false
  private val isIndexingTriggered = AtomicBoolean(false)

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
    try {
      if (isFirstIndexing) onFirstIndexingStart()

      val indexableActions = getIndexableActions()
      project?.let {
        withBackgroundProgress(it, EmbeddingsBundle.getMessage("ml.embeddings.indices.actions.generation.label")) {
          reportProgress(indexableActions.size) { reporter ->
            iterateActions(indexableActions, reporter)
          }
        }
      } ?: iterateActions(indexableActions)
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

  private fun onFirstIndexingStart() {
    ActionEmbeddingsStorage.getInstance().index.onIndexingStart()
  }

  private suspend fun onFirstIndexingFinish() {
    ActionEmbeddingsStorage.getInstance().index.onIndexingFinish()
    if (shouldSaveToDisk) {
      withContext(Dispatchers.IO) {
        ActionEmbeddingsStorage.getInstance().index.saveToDisk()
      }
    }
  }

  private suspend fun iterateActions(actions: List<IndexQueueEntry>, reporter: ProgressReporter? = null) {
    val index = ActionEmbeddingsStorage.getInstance().index
    val embeddingService = serviceAsync<LocalEmbeddingServiceProvider>().getService() ?: return

    suspend fun processAction(entry: IndexQueueEntry) {
      if (index.contains(entry.actionId)) return
      val embedding = embeddingService.embed(entry.templateText).normalized()
      shouldSaveToDisk = true
      index.addEntries(listOf(entry.actionId to embedding))
    }

    for (entry in actions) {
      reporter?.itemStep { processAction(entry) } ?: processAction(entry)
    }
  }

  private suspend fun loadRequirements(project: Project?) {
    withContext(Dispatchers.IO) {
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        launch {
          LocalArtifactsManager.getInstance().downloadArtifactsIfNecessary(project, retryIfCanceled = false)
        }
      }
      ActionEmbeddingsStorage.getInstance().index.loadFromDisk()
    }
  }

  companion object {
    private val LOG = Logger.getInstance(ActionEmbeddingStorageManager::class.java)

    fun getInstance(): ActionEmbeddingStorageManager = service()

    private fun shouldIndexAction(action: AnAction): Boolean {
      return !(action is ActionGroup && !action.isSearchable) && action.templatePresentation.hasText()
    }

    private suspend fun getIndexableActions(): List<IndexQueueEntry> {
      val actionManager = serviceAsync<ActionManager>() as ActionManagerImpl
      return readAction {
        actionManager
          .actions(canReturnStub = true)
          .filter { shouldIndexAction(it) }
          .mapNotNull { action ->
            val id = actionManager.getId(action)
            val templateText = action.templateText
            if (id == null || templateText == null) null else IndexQueueEntry(id, templateText)
          }
          .toList()
      }
    }

    private data class IndexQueueEntry(val actionId: String, val templateText: String)
  }
}