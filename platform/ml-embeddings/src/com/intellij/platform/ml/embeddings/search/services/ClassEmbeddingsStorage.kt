// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ml.embeddings.EmbeddingsBundle
import com.intellij.platform.ml.embeddings.utils.splitIdentifierIntoTokens
import com.intellij.psi.PsiFile
import com.intellij.platform.ml.embeddings.search.indices.DiskSynchronizedEmbeddingSearchIndex
import com.intellij.platform.ml.embeddings.search.indices.FileIndexableEntitiesProvider
import com.intellij.platform.ml.embeddings.search.indices.IndexableEntity
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager.Companion.SEMANTIC_SEARCH_RESOURCES_DIR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Thread-safe service for semantic classes search.
 * Supports Java Kotlin classes.
 * Holds a state with embeddings for each available indexable item and persists it on disk after calculation.
 * Generates the embeddings for classes not present in the loaded state at the IDE startup event if semantic classes search is enabled.
 */
@Service(Service.Level.PROJECT)
class ClassEmbeddingsStorage(project: Project, cs: CoroutineScope) : FileContentBasedEmbeddingsStorage<IndexableClass>(project, cs) {
  override val index = DiskSynchronizedEmbeddingSearchIndex(
    project.getProjectCachePath(
      File(SEMANTIC_SEARCH_RESOURCES_DIR)
        .resolve(LocalArtifactsManager.getInstance().getModelVersion())
        .resolve(INDEX_DIR).toString()
    )
  )

  override val scanningTitle
    get() = EmbeddingsBundle.getMessage("ml.embeddings.indices.classes.scanning.label")
  override val setupTitle
    get() = EmbeddingsBundle.getMessage("ml.embeddings.indices.classes.generation.label")

  override val spanIndexName = "semanticClasses"

  override val indexMemoryWeight: Int = 1
  override val indexStrongLimit = Registry.intValue("search.everywhere.ml.semantic.indexing.indexable.classes.limit")

  override fun traversePsiFile(file: PsiFile): Flow<IndexableClass> = FileIndexableEntitiesProvider.extractClasses(file)

  companion object {
    private const val INDEX_DIR = "classes"

    fun getInstance(project: Project): ClassEmbeddingsStorage = project.service()
  }
}

open class IndexableClass(override val id: String) : IndexableEntity {
  override val indexableRepresentation: String by lazy { splitIdentifierIntoTokens(id).joinToString(separator = " ") }
}