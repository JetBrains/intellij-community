// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.searcher.vfs

import com.intellij.openapi.application.readActionUndispatched
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ml.embeddings.EmbeddingsBundle
import com.intellij.platform.ml.embeddings.indexer.ClassesProvider
import com.intellij.platform.ml.embeddings.indexer.SymbolsProvider
import com.intellij.platform.ml.embeddings.indexer.TOTAL_THREAD_LIMIT_FOR_INDEXING
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableEntity
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableFile
import com.intellij.platform.ml.embeddings.indexer.extractAndAddEntities
import com.intellij.platform.ml.embeddings.indexer.searcher.EmbeddingEntitiesIndexer
import com.intellij.platform.ml.embeddings.indexer.searcher.SemanticSearchFileChangeListener
import com.intellij.platform.ml.embeddings.settings.EmbeddingIndexSettings
import com.intellij.platform.ml.embeddings.settings.EmbeddingIndexSettingsImpl
import com.intellij.platform.ml.embeddings.utils.SEMANTIC_SEARCH_TRACER
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiManager
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.indexing.IndexingDataKeys.PSI_FILE
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val SCANNING_SPAN_NAME = "embeddingFilesScanning"
private const val FILE_WORKER_COUNT = 4
private val logger = Logger.getInstance(VFSBasedEmbeddingEntitiesIndexer::class.java)

internal class VFSBasedEmbeddingEntitiesIndexer(private val cs: CoroutineScope) : EmbeddingEntitiesIndexer {
  private val isFileListenerAdded = AtomicBoolean(false)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val indexingScope = cs.childScope("VFSBasedEmbeddingEntitiesSearcher indexing scope", Dispatchers.Default.limitedParallelism(TOTAL_THREAD_LIMIT_FOR_INDEXING))

  private val filesLimit: Int?
    get() {
      return if (Registry.`is`("intellij.platform.ml.embeddings.index.files.use.limit")) {
        Registry.intValue("intellij.platform.ml.embeddings.index.files.limit")
      }
      else null
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  private val filesIterationContext = Dispatchers.Default.limitedParallelism(FILE_WORKER_COUNT)

  override suspend fun index(project: Project, settings: EmbeddingIndexSettings) {
    if (isFileListenerAdded.compareAndSet(false, true)) addFileListener()

    indexingScope.launch {
      val files = scanFiles(project).sortedByDescending { it.name.length }
      extractAndAddEntities(project) { filesChannel, classesChannel, symbolsChannel ->
        launch {
          search(project, files, filesChannel, classesChannel, symbolsChannel)
        }
      }
    }.join()
  }

  private fun addFileListener() {
    val listener = SemanticSearchFileChangeListener(cs.childScope("Embedding file change listener scope"), ::index)
    VirtualFileManager.getInstance().addAsyncFileListener(listener, this)
  }

  suspend fun index(project: Project, files: List<VirtualFile>) {
    val settings = EmbeddingIndexSettingsImpl.getInstance()
    if (!settings.shouldIndexAnythingFileBased) return

    indexingScope.launch {
      extractAndAddEntities(project) { filesChannel, classesChannel, symbolsChannel ->
        launch {
          search(project, files, filesChannel, classesChannel, symbolsChannel)
        }
      }
    }.join()
  }

  private suspend fun search(
    project: Project,
    files: List<VirtualFile>,
    filesChannel: Channel<IndexableEntity>?,
    classesChannel: Channel<IndexableEntity>?,
    symbolsChannel: Channel<IndexableEntity>?,
  ) {
    val psiManager = PsiManager.getInstance(project)
    val processedFiles = AtomicInteger(0)
    val total: Int = filesLimit?.let { minOf(files.size, it) } ?: files.size
    logger.debug { "Effective embedding indexing files limit: $total" }

    withContext(filesIterationContext) {
      val limit = filesLimit
      repeat(FILE_WORKER_COUNT) { worker ->
        var index = worker
        launch {
          while (index < files.size) {
            if (limit != null && processedFiles.get() >= limit) return@launch
            val file = files[index]
            if (file.isFile && file.isValid && file.isInLocalFileSystem) {
              processFile(file, psiManager, filesChannel, classesChannel, symbolsChannel)
              processedFiles.incrementAndGet()
            }
            else {
              logger.debug { "File is not valid: ${file.name}" }
            }
            index += FILE_WORKER_COUNT
          }
        }
      }
    }
  }

  private suspend fun scanFiles(project: Project): List<VirtualFile> {
    val scanLimit = filesLimit?.let { it * 2 } // do not scan all files if there is a limit
    var filteredFiles = 0
    return SEMANTIC_SEARCH_TRACER.spanBuilder(SCANNING_SPAN_NAME).useWithScope {
      withBackgroundProgress(project, EmbeddingsBundle.getMessage("ml.embeddings.indices.scanning.label")) {
        val files = mutableListOf<VirtualFile>()
        ProjectFileIndex.getInstance(project).iterateContent { file ->
          if (file.isFile && file.isValid && file.isInLocalFileSystem) {
            files.add(file)
            filteredFiles += 1
          }
          scanLimit == null || filteredFiles < scanLimit
        }
        files
      }
    }
  }

  private suspend fun processFile(
    file: VirtualFile,
    psiManager: PsiManager,
    filesChannel: Channel<IndexableEntity>?,
    classesChannel: Channel<IndexableEntity>?,
    symbolsChannel: Channel<IndexableEntity>?,
  ) = coroutineScope {
    if (filesChannel != null) {
      launch {
        filesChannel.send(IndexableFile(file))
      }
    }

    if (classesChannel != null || symbolsChannel != null) {
      val (psiFile, fileType) = readActionUndispatched {
        val psiFile = psiManager.findFile(file) ?: return@readActionUndispatched null
        val fileType = psiFile.fileType
        psiFile to fileType
      } ?: return@coroutineScope

      val content: FileContentImpl = FileContentImpl.createByFile(file, psiManager.project) as FileContentImpl
      content.putUserData(PSI_FILE, psiFile) // todo I think we can avoid explicit passing of file
      content.setSubstituteFileType(fileType) // todo we don't want to infer the substituted file type, do we?

      // we can't run processing concurrently because LighterAST is not thread-safe
      if (classesChannel != null) {
        val classes = readActionUndispatched { ClassesProvider.extractClasses(content) }
        classes.forEach { classesChannel.send(it) }
      }

      if (symbolsChannel != null) {
        val symbols = readActionUndispatched { SymbolsProvider.extractSymbols(content) }
        symbols.forEach { symbolsChannel.send(it) }
      }
    }
  }

  override fun dispose() = Unit
}
