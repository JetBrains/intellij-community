// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readActionUndispatched
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ml.embeddings.EmbeddingsBundle
import com.intellij.platform.ml.embeddings.local.NativeServerManager
import com.intellij.platform.ml.embeddings.logging.EmbeddingSearchLogger
import com.intellij.platform.ml.embeddings.search.indices.EntitySourceType
import com.intellij.platform.ml.embeddings.search.indices.FileIndexableEntitiesProvider
import com.intellij.platform.ml.embeddings.search.indices.IndexableEntity
import com.intellij.platform.ml.embeddings.search.utils.SEMANTIC_SEARCH_TRACER
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiManager
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.*
import org.jetbrains.embeddings.local.server.stubs.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

// TODO: consider making app-level
@Service(Service.Level.PROJECT)
class FileBasedEmbeddingsManager(private val project: Project, private val cs: CoroutineScope) : Disposable {
  private val indexingScope = cs.childScope("Embedding Indexing Scope")
  private val isIndexingTriggered = AtomicBoolean(false)

  // TODO: use persistent string enumerator to save RAM
  //private val projectIndicesRoot = LocalArtifactsManager.indicesRoot.resolve(project.getProjectCacheFileName())

  //private val enumeratorRoot = projectIndicesRoot
  //  .resolve("enumerator")
  //  .resolve("ids.enum")
  //  .also { Files.createDirectories(it.parent) }

  //private val textEnumerator = PersistentStringEnumerator(
  //  enumeratorRoot, 1024 * 1024, true, null)

  private val textEnumerator = TemporaryStringEnumerator()

  @OptIn(ExperimentalCoroutinesApi::class)
  private val indexingContext = Dispatchers.Default.limitedParallelism(8)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val filesIterationContext = Dispatchers.Default.limitedParallelism(FILE_WORKER_COUNT)

  private val filesLimit: Int?
    get() {
      return if (Registry.`is`("intellij.platform.ml.embeddings.index.files.use.limit")) {
        Registry.intValue("intellij.platform.ml.embeddings.index.files.limit")
      }
      else null
    }

  fun prepareForSearch(): Job = cs.launch {
    if (isIndexingTriggered.compareAndSet(false, true)) addFileListener()
    indexingScope.coroutineContext.cancelChildren()
    withContext(indexingScope.coroutineContext) {
      project.waitForSmartMode()
      indexProject()
    }
  }

  fun triggerIndexing() {
    if (isIndexingTriggered.compareAndSet(false, true)) {
      addFileListener()
      prepareForSearch()
    }
  }

  private fun addFileListener() {
    VirtualFileManager.getInstance().addAsyncFileListener(
      SemanticSearchFileChangeListener.getInstance(project),
      SemanticSearchCoroutineScope.getInstance(project)
    )
  }

  private suspend fun indexProject() {
    logger.debug { "Started full project embedding indexing" }
    SEMANTIC_SEARCH_TRACER.spanBuilder(FileBasedEmbeddingStoragesManager.INDEXING_SPAN_NAME).useWithScope {
      val projectIndexingStartTime = System.nanoTime()
      indexFiles(scanFiles().toList().sortedByDescending { it.name.length })
      EmbeddingSearchLogger.indexingFinished(project, forActions = false, TimeoutUtil.getDurationMillis(projectIndexingStartTime))
    }
    logger.debug { "Finished full project embedding indexing" }
  }

  private fun scanFiles(): Flow<VirtualFile> {
    val scanLimit = filesLimit?.let { it * 2 } // do not scan all files if there is a limit
    var filteredFiles = 0
    return channelFlow {
      SEMANTIC_SEARCH_TRACER.spanBuilder(FileBasedEmbeddingStoragesManager.SCANNING_SPAN_NAME).useWithScope {
        withBackgroundProgress(project, EmbeddingsBundle.getMessage("ml.embeddings.indices.scanning.label")) {
          ProjectFileIndex.getInstance(project).iterateContent { file ->
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

  suspend fun getIndexableRepresentation(id: Int): String? {
    return textEnumerator.valueOf(id)
  }

  // TODO: enumerators should be separate for classes/files/symbols
  private suspend fun getPersistentId(name: String): Int {
    return textEnumerator.enumerate(name)
  }

  suspend fun indexFiles(files: List<VirtualFile>, sourceType: EntitySourceType = EntitySourceType.DEFAULT) {
    val settings = EmbeddingIndexSettingsImpl.getInstance()
    if (!settings.shouldIndexAnythingFileBased) return

    val connection = NativeServerManager.getInstance().getConnection()

    withContext(indexingScope.coroutineContext) {
      withContext(indexingContext) {
        val bufferSize = BATCH_SIZE * 8
        val filesChannel = Channel<IndexableFile>(capacity = bufferSize)
        val classesChannel = Channel<IndexableClass>(capacity = bufferSize)
        val symbolsChannel = Channel<IndexableSymbol>(capacity = bufferSize)
        val currentProjectId = project.getProjectCacheFileName()

        suspend fun sendEntities(indexId: String, channel: ReceiveChannel<IndexableEntity>) {
          val entityList = ArrayList<Embeddings.index_entity>(BATCH_SIZE)
          var index = 0

          for (indexableFile in channel) {
            val entity = indexEntity {
              id = getPersistentId(indexableFile.id.id + "#" + indexableFile.indexableRepresentation)
              text = indexableFile.indexableRepresentation.take(64)
            }
            if (entityList.size < BATCH_SIZE) entityList.add(entity) else entityList[index] = entity
            ++index
            if (index == BATCH_SIZE) {
              connection.ensureVectorsPresent(presentRequest {
                projectId = currentProjectId
                indexType = indexId
                entities.addAll(entityList)
              })
              index = 0
            }
          }
          if (entityList.isNotEmpty()) {
            connection.ensureVectorsPresent(presentRequest {
              projectId = currentProjectId
              indexType = indexId
              entities.addAll(entityList)
            })
          }
        }

        // files processor
        launch {
          sendEntities("files", filesChannel)
        }

        // classes processor
        launch {
          sendEntities("classes", classesChannel)
        }

        // symbols processor
        launch {
          sendEntities("symbols", symbolsChannel)
        }

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
                  processFile(file, psiManager, settings, filesChannel, classesChannel, symbolsChannel)
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
        filesChannel.close()
        classesChannel.close()
        symbolsChannel.close()
        // connection.finishIndexingSession(finishRequest {})
      }
    }
  }

  private suspend fun processFile(
    file: VirtualFile,
    psiManager: PsiManager,
    settings: EmbeddingIndexSettings,
    filesChannel: Channel<IndexableFile>,
    classesChannel: Channel<IndexableClass>,
    symbolsChannel: Channel<IndexableSymbol>,
  ) = coroutineScope {
    if (settings.shouldIndexFiles) {
      launch {
        filesChannel.send(IndexableFile(file))
      }
    }

    if (settings.shouldIndexClasses || settings.shouldIndexSymbols) {
      val psiFile = readActionUndispatched { psiManager.findFile(file) } ?: return@coroutineScope

      if (settings.shouldIndexClasses) {
        launch {
          readActionUndispatched { FileIndexableEntitiesProvider.extractClasses(psiFile) }.collect(classesChannel::send)
        }
      }
      if (settings.shouldIndexSymbols) {
        launch {
          readActionUndispatched { FileIndexableEntitiesProvider.extractSymbols(psiFile) }.collect(symbolsChannel::send)
        }
      }
    }
  }

  companion object {
    fun getInstance(project: Project): FileBasedEmbeddingsManager = project.service()

    internal const val INDEXING_VERSION = "0.0.1"

    private val logger = Logger.getInstance(FileBasedEmbeddingsManager::class.java)

    private const val FILE_WORKER_COUNT = 4

    private const val BATCH_SIZE = 128
  }

  override fun dispose() {
    // textEnumerator.close()
  }
}