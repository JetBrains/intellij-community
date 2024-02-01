// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ml.embeddings.EmbeddingsBundle
import com.intellij.platform.ml.embeddings.search.indices.FileIndexableEntitiesProvider
import com.intellij.platform.ml.embeddings.search.utils.SEMANTIC_SEARCH_TRACER
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager
import com.intellij.platform.util.coroutines.namedChildScope
import com.intellij.platform.util.progress.reportProgress
import com.intellij.psi.PsiManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Service(Service.Level.PROJECT)
class FileBasedEmbeddingStoragesManager(private val project: Project, private val cs: CoroutineScope) {
  private val indexingScope = cs.namedChildScope("Embedding indexing scope")
  private var isFirstIndexing = true

  private val filesLimit: Int?
    get() {
      return if (Registry.`is`("intellij.platform.ml.embeddings.index.files.use.limit")) {
        Registry.intValue("intellij.platform.ml.embeddings.index.files.limit")
      }
      else null
    }

  fun prepareForSearch() = cs.launch {
    indexingScope.coroutineContext.cancelChildren()
    withContext(indexingScope.coroutineContext) {
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        project.waitForSmartMode() // project may become dumb again, but we don't interfere initial indexing
        loadRequirements()
      }
      indexProject()
    }
  }

  private suspend fun loadRequirements() {
    withContext(Dispatchers.IO) {
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        launch {
          LocalArtifactsManager.getInstance().downloadArtifactsIfNecessary(project, retryIfCanceled = false)
        }
      }
      val settings = EmbeddingIndexSettingsImpl.getInstance(project)
      launch {
        if (settings.shouldIndexFiles) {
          val index = FileEmbeddingsStorage.getInstance(project).index
          EmbeddingIndexMemoryManager.getInstance().registerIndex(index)
          index.loadFromDisk()
          logger.debug { "Loaded files embedding index from disk, size: ${index.size}, root: ${index.root}" }
        }
      }
      launch {
        if (settings.shouldIndexClasses) {
          val index = ClassEmbeddingsStorage.getInstance(project).index
          EmbeddingIndexMemoryManager.getInstance().registerIndex(index)
          index.loadFromDisk()
          logger.debug { "Loaded classes embedding index from disk, size: ${index.size}, root: ${index.root}" }
        }
      }
      launch {
        if (settings.shouldIndexSymbols) {
          val index = SymbolEmbeddingStorage.getInstance(project).index
          EmbeddingIndexMemoryManager.getInstance().registerIndex(index)
          index.loadFromDisk()
          logger.debug { "Loaded symbols embedding index from disk, size: ${index.size}, root: ${index.root}" }
        }
      }
    }
  }

  private suspend fun indexProject() {
    logger.debug { "Started full project embedding indexing" }
    SEMANTIC_SEARCH_TRACER.spanBuilder(INDEXING_SPAN_NAME).useWithScope {
      try {
        indexFiles(scanFiles().toList())
      }
      catch (e: CancellationException) {
        logger.debug { "Full project embedding indexing was cancelled" }
        throw e
      }
      finally {
        if (isFirstIndexing) {
          onFirstIndexingFinish()
          isFirstIndexing = false
        }
      }
    }
    logger.debug { "Finished full project embedding indexing" }
  }

  suspend fun indexFiles(files: List<VirtualFile>) {
    val settings = EmbeddingIndexSettingsImpl.getInstance(project)
    if (!settings.shouldIndexAnything) return
    withContext(indexingScope.coroutineContext) {
      if (isFirstIndexing) onFirstIndexingStart()
      val psiManager = PsiManager.getInstance(project)
      var processedFiles = 0
      withBackgroundProgress(project, EmbeddingsBundle.getMessage("ml.embeddings.indices.generation.label")) {
        val total: Int = filesLimit?.let { minOf(files.size, it) } ?: files.size
        reportProgress(total) { reporter ->
          files.forEach { file ->
            val limit = filesLimit
            if (!EmbeddingIndexMemoryManager.getInstance().checkCanAddEntry()) return@reportProgress
            if (limit == null || processedFiles < limit) {
              if (file.isFile && file.isValid && file.isInLocalFileSystem) {
                reporter.itemStep(file.presentableName) {
                  if (settings.shouldIndexFiles) { // TODO: consider caching this value
                    launch {
                      val entity = IndexableFile(file)
                      val index = FileEmbeddingsStorage.getInstance(project).index
                      if (entity.id in index) return@launch
                      EmbeddingIndexingTask.Add(listOf(entity.id), listOf(entity.indexableRepresentation)).run(index)
                    }
                  }
                  if (settings.shouldIndexClasses) {
                    launch {
                      val entities = readAction {
                        FileIndexableEntitiesProvider.extractClasses(psiManager.findFile(file) ?: return@readAction emptyFlow())
                      }
                      val index = ClassEmbeddingsStorage.getInstance(project).index
                      entities.filter { it.id !in index }.collect {
                        EmbeddingIndexingTask.Add(listOf(it.id), listOf(it.indexableRepresentation)).run(index)
                      }
                    }
                  }
                  if (settings.shouldIndexSymbols) {
                    launch {
                      val entities = readAction {
                        FileIndexableEntitiesProvider.extractSymbols(psiManager.findFile(file) ?: return@readAction emptyFlow())
                      }
                      val index = SymbolEmbeddingStorage.getInstance(project).index
                      entities.filter { it.id !in index }.collect {
                        EmbeddingIndexingTask.Add(listOf(it.id), listOf(it.indexableRepresentation)).run(index)
                      }
                    }
                  }
                  ++processedFiles
                }
              }
            }
          }
        }
      }
    }
  }

  private fun scanFiles(): Flow<VirtualFile> {
    val scanLimit = filesLimit?.let { it * 2 } // do not scan all files if there is a limit
    var filteredFiles = 0
    return channelFlow {
      SEMANTIC_SEARCH_TRACER.spanBuilder(SCANNING_SPAN_NAME).useWithScope(coroutineContext) {
        withBackgroundProgress(project, EmbeddingsBundle.getMessage("ml.embeddings.indices.scanning.label")) {
          // ProjectFileIndex.getInstance(project).iterateContent { file ->
          ProjectRootManager.getInstance(project).contentSourceRoots.forEach { root ->
            VfsUtilCore.iterateChildrenRecursively(root, null) { file ->
              if (file.isFile && file.isValid && file.isInLocalFileSystem) {
                launch { send(file) }
                filteredFiles += 1
              }
              scanLimit == null || filteredFiles < scanLimit
            }
          }
        }
      }
    }
  }

  @Suppress("unused")
  fun stopIndexingIfDisabled() {
    if (!EmbeddingIndexSettingsImpl.getInstance(project).shouldIndexAnything) {
      indexingScope.coroutineContext.cancelChildren()
    }
  }

  private fun onFirstIndexingStart() {
    val settings = EmbeddingIndexSettingsImpl.getInstance(project)
    if (settings.shouldIndexFiles) {
      FileEmbeddingsStorage.getInstance(project).index.onIndexingStart()
    }
    if (settings.shouldIndexClasses) {
      ClassEmbeddingsStorage.getInstance(project).index.onIndexingStart()
    }
    if (settings.shouldIndexSymbols) {
      SymbolEmbeddingStorage.getInstance(project).index.onIndexingStart()
    }
  }

  private fun onFirstIndexingFinish() {
    val settings = EmbeddingIndexSettingsImpl.getInstance(project)
    if (settings.shouldIndexFiles) {
      val index = FileEmbeddingsStorage.getInstance(project).index
      index.onIndexingFinish()
      if (index.changed) {
        cs.launch(Dispatchers.IO) {
          index.saveToDisk()
        }
      }
    }
    if (settings.shouldIndexClasses) {
      val index = ClassEmbeddingsStorage.getInstance(project).index
      index.onIndexingFinish()
      if (index.changed) {
        cs.launch(Dispatchers.IO) {
          index.saveToDisk()
        }
      }
    }
    if (settings.shouldIndexSymbols) {
      val index = SymbolEmbeddingStorage.getInstance(project).index
      index.onIndexingFinish()
      if (index.changed) {
        cs.launch(Dispatchers.IO) {
          index.saveToDisk()
        }
      }
    }
  }

  companion object {
    fun getInstance(project: Project): FileBasedEmbeddingStoragesManager = project.service()

    private val logger = Logger.getInstance(FileBasedEmbeddingStoragesManager::class.java)

    const val SCANNING_SPAN_NAME = "embeddingFilesScanning"
    const val INDEXING_SPAN_NAME = "embeddingIndexing"
  }
}