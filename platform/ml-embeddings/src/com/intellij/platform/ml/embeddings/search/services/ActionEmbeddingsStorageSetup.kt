// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.ml.embeddings.search.indices.EmbeddingSearchIndex
import com.intellij.platform.ml.embeddings.services.LocalEmbeddingServiceProvider
import com.intellij.platform.ml.embeddings.utils.normalized
import com.intellij.platform.util.progress.durationStep
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

internal class ActionEmbeddingsStorageSetup(
  private val index: EmbeddingSearchIndex,
  private val indexSetupJob: AtomicReference<Job>
) {
  private var shouldSaveToDisk = false

  suspend fun run() = coroutineScope {
    val actionManager = (serviceAsync<ActionManager>() as ActionManagerImpl)

    val indexableActions = ActionEmbeddingsStorage.getIndexableActions(actionManager)

    val embeddingService = serviceAsync<LocalEmbeddingServiceProvider>().getService() ?: return@coroutineScope
    // Cancel the previous embeddings calculation task if it's not finished
    indexSetupJob.getAndSet(launch {
      var indexedActionsCount = 0
      val totalIndexableActionsCount = indexableActions.size

      index.onIndexingStart()
      indexableActions
        .asSequence()
        .mapNotNull {
          val id = actionManager.getId(it)
          if (id == null || index.contains(id)) {
            null
          }
          else {
            id to it
          }
        }
        .chunked(BATCH_SIZE)
        .forEach { batch ->
          val actionIds = batch.map { (id, _) -> id }
          val texts = batch.map { (_, action) -> action.templateText!! }

          durationStep(texts.size.toDouble() / totalIndexableActionsCount) {
            val embeddings = embeddingService.embed(texts).map { it.normalized() }
            shouldSaveToDisk = true
            ++indexedActionsCount
            index.addEntries(actionIds zip embeddings)
          }
        }
      // Finish the progress reporter
      durationStep((totalIndexableActionsCount - indexedActionsCount).toDouble() / totalIndexableActionsCount) {}
      index.onIndexingFinish()
    })?.cancel()
  }

  fun onFinish(cs: CoroutineScope) {
    indexSetupJob.set(null)
    if (shouldSaveToDisk) {
      cs.launch(Dispatchers.IO) {
        index.saveToDisk()
      }
    }
  }

  companion object {
    private const val BATCH_SIZE = 1
  }
}