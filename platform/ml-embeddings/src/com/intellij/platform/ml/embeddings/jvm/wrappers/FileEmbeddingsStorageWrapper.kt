// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.jvm.wrappers

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ml.embeddings.indexer.IndexId.FILES
import com.intellij.platform.ml.embeddings.settings.EmbeddingIndexSettingsImpl
import kotlinx.coroutines.CoroutineScope

/**
 * Thread-safe service for semantic files search.
 * Holds a state with embeddings for each available project file and persists it on disk after calculation.
 * Generate the embeddings for files not present in the loaded state at the IDE startup event if semantic files search is enabled.
 */
@Service(Service.Level.PROJECT)
class FileEmbeddingsStorageWrapper(project: Project, coroutineScope: CoroutineScope)
  : AbstractEmbeddingsStorageWrapper(project, FILES, coroutineScope) {
  override fun isEnabled(): Boolean = EmbeddingIndexSettingsImpl.getInstance().shouldIndexFiles

  companion object {
    fun getInstance(project: Project): FileEmbeddingsStorageWrapper = project.service()
  }
}
