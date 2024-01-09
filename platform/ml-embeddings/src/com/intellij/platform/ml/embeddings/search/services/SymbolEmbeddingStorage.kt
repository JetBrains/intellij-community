// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ml.embeddings.EmbeddingsBundle
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager.Companion.SEMANTIC_SEARCH_RESOURCES_DIR
import com.intellij.platform.ml.embeddings.utils.splitIdentifierIntoTokens
import com.intellij.psi.PsiFile
import com.intellij.platform.ml.embeddings.search.indices.DiskSynchronizedEmbeddingSearchIndex
import com.intellij.platform.ml.embeddings.search.indices.FileIndexableEntitiesProvider
import com.intellij.platform.ml.embeddings.search.indices.IndexableEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Thread-safe service for semantic symbols search.
 * Supports Java methods and Kotlin functions.
 * Holds a state with embeddings for each available indexable item and persists it on disk after calculation.
 * Generates the embeddings for symbols not present in the loaded state at the IDE startup event if semantic symbols search is enabled.
 */
@Service(Service.Level.PROJECT)
class SymbolEmbeddingStorage(project: Project, cs: CoroutineScope)
  : FileContentBasedEmbeddingsStorage<IndexableSymbol>(project, cs) {
  override val index = DiskSynchronizedEmbeddingSearchIndex(
    project.getProjectCachePath(
      File(SEMANTIC_SEARCH_RESOURCES_DIR)
        .resolve(LocalArtifactsManager.getInstance().getModelVersion())
        .resolve(INDEX_DIR).toString()
    )
  )

  override val scanningTitle
    get() = EmbeddingsBundle.getMessage("ml.embeddings.indices.symbols.scanning.label")
  override val setupTitle
    get() = EmbeddingsBundle.getMessage("ml.embeddings.indices.symbols.generation.label")

  override val spanIndexName = "semanticSymbols"

  override val indexMemoryWeight: Int = 2
  override val indexStrongLimit = Registry.intValue("search.everywhere.ml.semantic.indexing.indexable.symbols.limit")

  override fun traversePsiFile(file: PsiFile): Flow<IndexableSymbol> = FileIndexableEntitiesProvider.extractSymbols(file)

  companion object {
    private const val INDEX_DIR = "symbols"

    fun getInstance(project: Project): SymbolEmbeddingStorage = project.service()
  }
}

open class IndexableSymbol(override val id: String) : IndexableEntity {
  override val indexableRepresentation: String by lazy { splitIdentifierIntoTokens(id).joinToString(separator = " ") }
}