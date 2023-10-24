// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.ml.embeddings.EmbeddingsBundle
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager.Companion.SEMANTIC_SEARCH_RESOURCES_DIR
import com.intellij.platform.ml.embeddings.utils.splitIdentifierIntoTokens
import com.intellij.platform.ml.embeddings.search.indices.DiskSynchronizedEmbeddingSearchIndex
import com.intellij.platform.ml.embeddings.search.indices.IndexableEntity
import com.intellij.platform.ml.embeddings.search.settings.SemanticSearchSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * Thread-safe service for semantic files search.
 * Holds a state with embeddings for each available project file and persists it on disk after calculation.
 * Generates the embeddings for files not present in the loaded state at the IDE startup event if semantic files search is enabled.
 */
@Service(Service.Level.PROJECT)
class FileEmbeddingsStorage(project: Project, private val cs: CoroutineScope)
  : DiskSynchronizedEmbeddingsStorage<IndexableFile>(project, cs), Disposable {
  // At unique path based on project location in a file system
  override val index = DiskSynchronizedEmbeddingSearchIndex(
    project.getProjectCachePath(
      File(SEMANTIC_SEARCH_RESOURCES_DIR)
        .resolve(LocalArtifactsManager.getInstance().getModelVersion())
        .resolve(INDEX_DIR).toString()
    )
  )

  override val scanningTitle
    get() = EmbeddingsBundle.getMessage("ml.embeddings.indices.files.scanning.label")
  override val setupTitle
    get() = EmbeddingsBundle.getMessage("ml.embeddings.indices.files.generation.label")

  override val spanIndexName = "semanticFiles"

  override val indexMemoryWeight: Int = 1
  override val indexStrongLimit = Registry.intValue("search.everywhere.ml.semantic.indexing.indexable.files.limit")

  override fun checkSearchEnabled() = SemanticSearchSettings.getInstance().enabledInFilesTab

  override suspend fun getIndexableEntities(): List<IndexableFile> {
    // It's important that we do not block write actions here:
    // If the write action is invoked, the read action is restarted
    return readAction {
      buildList {
        ProjectFileIndex.getInstance(project).iterateContent {
          if (it.isFile and it.isInLocalFileSystem) add(IndexableFile(it))
          true
        }
      }
    }
  }

  fun renameFile(oldFileName: String, newFile: IndexableFile) {
    if (!checkSearchEnabled()) return
    cs.launch {
      indexSetupJob.get()?.join()
      EmbeddingIndexingTask.RenameDiskSynchronized(
        oldFileName.intern(), newFile.id.intern(), newFile.indexableRepresentation.intern()
      ).run(index)
    }
  }

  companion object {
    private const val INDEX_DIR = "files"

    fun getInstance(project: Project): FileEmbeddingsStorage = project.service()
  }

  override fun dispose() = Unit
}

class IndexableFile(file: VirtualFile) : IndexableEntity {
  override val id = file.name.intern()
  override val indexableRepresentation by lazy { splitIdentifierIntoTokens(file.nameWithoutExtension).joinToString(separator = " ") }
}