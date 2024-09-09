// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.platform.ml.embeddings.search.indices.DiskSynchronizedEmbeddingSearchIndex
import com.intellij.platform.ml.embeddings.search.indices.EntityId
import com.intellij.platform.ml.embeddings.search.indices.IndexType.SYMBOLS
import com.intellij.platform.ml.embeddings.search.indices.IndexableEntity
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager.Companion.SEMANTIC_SEARCH_RESOURCES_DIR_NAME
import com.intellij.platform.ml.embeddings.utils.splitIdentifierIntoTokens
import kotlinx.coroutines.CoroutineScope
import java.io.File

/**
 * Thread-safe service for semantic symbols search.
 * Supports Java methods and Kotlin functions.
 * Holds a state with embeddings for each available indexable item.
 */
@Service(Service.Level.PROJECT)
class SymbolEmbeddingStorage(project: Project, cs: CoroutineScope)
  : DiskSynchronizedEmbeddingsStorage<IndexableSymbol>(project, cs) {
  override val index = DiskSynchronizedEmbeddingSearchIndex(
    project.getProjectCachePath(
      File(SEMANTIC_SEARCH_RESOURCES_DIR_NAME)
        .resolve(OLD_API_DIR_NAME)
        .resolve(INDEX_DIR)
        .toString()
    )
  )

  override val reportableIndex = SYMBOLS

  companion object {
    private const val INDEX_DIR = "symbols"

    fun getInstance(project: Project): SymbolEmbeddingStorage = project.service()
  }
}

open class IndexableSymbol(override val id: EntityId) : IndexableEntity {
  override val indexableRepresentation: String by lazy { splitIdentifierIntoTokens(id.id) }
}