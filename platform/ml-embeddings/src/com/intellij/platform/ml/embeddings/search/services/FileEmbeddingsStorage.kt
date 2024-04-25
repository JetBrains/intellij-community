// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ml.embeddings.logging.EmbeddingSearchLogger
import com.intellij.platform.ml.embeddings.search.indices.DiskSynchronizedEmbeddingSearchIndex
import com.intellij.platform.ml.embeddings.search.indices.IndexableEntity
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager.Companion.SEMANTIC_SEARCH_RESOURCES_DIR
import com.intellij.platform.ml.embeddings.utils.splitIdentifierIntoTokens
import kotlinx.coroutines.CoroutineScope
import java.io.File

/**
 * Thread-safe service for semantic files search.
 * Holds a state with embeddings for each available project file and persists it on disk after calculation.
 * Generate the embeddings for files not present in the loaded state at the IDE startup event if semantic files search is enabled.
 */
@Service(Service.Level.PROJECT)
class FileEmbeddingsStorage(project: Project, coroutineScope: CoroutineScope)
  : DiskSynchronizedEmbeddingsStorage<IndexableFile>(project, coroutineScope) {
  // At unique path based on project location in a file system
  override val index = DiskSynchronizedEmbeddingSearchIndex(
    project.getProjectCachePath(
      File(SEMANTIC_SEARCH_RESOURCES_DIR)
        .resolve(LocalArtifactsManager.getInstance().getModelVersion())
        .resolve(INDEX_DIR).toString()
    )
  )

  override val reportableIndex = EmbeddingSearchLogger.Index.FILES

  companion object {
    private const val INDEX_DIR = "files"

    fun getInstance(project: Project): FileEmbeddingsStorage = project.service()
  }
}

class IndexableFile(file: VirtualFile) : IndexableEntity {
  override val id = file.name.intern()
  override val indexableRepresentation by lazy { splitIdentifierIntoTokens(file.nameWithoutExtension) }
}