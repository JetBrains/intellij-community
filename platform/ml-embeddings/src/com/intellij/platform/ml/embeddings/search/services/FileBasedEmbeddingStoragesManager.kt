// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readActionUndispatched
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.diagnostic.telemetry.helpers.useWithScope
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ml.embeddings.EmbeddingsBundle
import com.intellij.platform.ml.embeddings.logging.EmbeddingSearchLogger
import com.intellij.platform.ml.embeddings.search.indices.*
import com.intellij.platform.ml.embeddings.search.indices.EntityActionType.ADD
import com.intellij.platform.ml.embeddings.search.indices.EntityActionType.REMOVE
import com.intellij.platform.ml.embeddings.search.indices.EntitySourceType.DEFAULT
import com.intellij.platform.ml.embeddings.search.utils.SEMANTIC_SEARCH_TRACER
import com.intellij.platform.ml.embeddings.services.LocalArtifactsManager
import com.intellij.platform.ml.embeddings.services.LocalEmbeddingServiceProvider
import com.intellij.platform.ml.embeddings.utils.normalized
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.coroutines.flow.debounceBatch
import com.intellij.psi.PsiManager
import com.intellij.util.TimeoutUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.PROJECT)
class FileBasedEmbeddingStoragesManager(private val project: Project, private val cs: CoroutineScope) {
  private val indexingScope = cs.childScope("Embedding indexing scope")
  private val indexingEventsScope = cs.childScope("Embedding events indexing scope")
  private var isFirstIndexing = true
  private val isIndexingTriggered = AtomicBoolean(false)
  private var indexLoaded = false

  @OptIn(ExperimentalCoroutinesApi::class)
  private val filesIterationContext = Dispatchers.Default.limitedParallelism(FILE_WORKER_COUNT)

  @OptIn(ExperimentalCoroutinesApi::class)
  private val indexingContext = Dispatchers.Default.limitedParallelism(8)

  private val indexEntitiesShards = List(EMBEDDING_WORKER_COUNT) { Channel<EntityIndexAction>(capacity = CHANNEL_CAPACITY) }

  init {
    for (shard in indexEntitiesShards) {
      cs.launch {
        withContext(indexingEventsScope.coroutineContext) {
          shard.consumeAsFlow().debounceBatch(50.milliseconds).collect { chunk ->
            chunk.chunked(BATCH_SIZE).forEach { batch ->
              processEntity(batch)
            }
          }
        }
      }
    }
  }

  private val filesLimit: Int?
    get() {
      return if (Registry.`is`("intellij.platform.ml.embeddings.index.files.use.limit")) {
        Registry.intValue("intellij.platform.ml.embeddings.index.files.limit")
      }
      else null
    }

  fun prepareForSearch() = cs.launch {
    if (isIndexingTriggered.compareAndSet(false, true)) addFileListener()
    indexingScope.coroutineContext.cancelChildren()
    withContext(indexingScope.coroutineContext) {
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        project.waitForSmartMode() // project may become dumb again, but we don't interfere initial indexing
        loadRequirements()
      }
      indexProject()
    }
  }

  fun triggerIndexing() {
    if (isIndexingTriggered.compareAndSet(false, true)) {
      addFileListener()
      prepareForSearch()
    }
  }

  suspend fun indexEntities(entities: List<EntityIndexAction>) {
    startIndexingSession()
    try {
      entities.forEach { entity ->
        val shard = entity.getShard()
        indexEntitiesShards[shard].send(entity)
      }
    }
    finally {
      finishIndexingSession()
    }
  }

  private fun EntityIndexAction.getShard() = abs(entity.indexableRepresentation.hashCode()) % EMBEDDING_WORKER_COUNT

  suspend fun dropIndex(sourceType: EntitySourceType) {
    startIndexingSession()

    try {
      withContext(Dispatchers.IO) {
        val settings = EmbeddingIndexSettingsImpl.getInstance()
        val filesIndexLoadingJob = launch {
          if (settings.shouldIndexFiles) {
            val index = FileEmbeddingsStorage.getInstance(project).index
            index.clearBySourceType(sourceType)
          }
        }
        val classIndexLoadingJob = launch {
          if (settings.shouldIndexClasses) {
            val index = ClassEmbeddingsStorage.getInstance(project).index
            index.clearBySourceType(sourceType)
          }
        }
        val symbolIndexLoadingJob = launch {
          if (settings.shouldIndexSymbols) {
            val index = SymbolEmbeddingStorage.getInstance(project).index
            index.clearBySourceType(sourceType)
          }
        }
        listOf(filesIndexLoadingJob, classIndexLoadingJob, symbolIndexLoadingJob).joinAll()
      }
    } finally {
      finishIndexingSession()
    }
  }

  private fun addFileListener() {
    VirtualFileManager.getInstance().addAsyncFileListener(
      SemanticSearchFileChangeListener.getInstance(project),
      SemanticSearchCoroutineScope.getInstance(project)
    )
  }

  private suspend fun loadRequirements() {
    withContext(Dispatchers.IO) {
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        launch {
          LocalArtifactsManager.getInstance().downloadArtifactsIfNecessary(project, retryIfCanceled = false)
        }
      }
      val settings = EmbeddingIndexSettingsImpl.getInstance()
      indexLoaded = true
      val indexLoadingStartTime = System.nanoTime()
      val filesIndexLoadingJob = launch {
        if (settings.shouldIndexFiles) {
          val index = FileEmbeddingsStorage.getInstance(project).index
          FileEmbeddingsStorage.getInstance(project).registerInMemoryManager()
          FileEmbeddingsStorage.getInstance(project).loadIndex()
          val indexSize = index.getSize()
          indexLoaded = indexLoaded && indexSize > 0
          logger.debug { "Loaded files embedding index from disk, size: ${indexSize}, root: ${index.root}" }
        }
      }
      val classIndexLoadingJob = launch {
        if (settings.shouldIndexClasses) {
          val index = ClassEmbeddingsStorage.getInstance(project).index
          ClassEmbeddingsStorage.getInstance(project).registerInMemoryManager()
          ClassEmbeddingsStorage.getInstance(project).loadIndex()
          val indexSize = index.getSize()
          indexLoaded = indexLoaded && indexSize > 0
          logger.debug { "Loaded classes embedding index from disk, size: ${indexSize}, root: ${index.root}" }
        }
      }
      val symbolIndexLoadingJob = launch {
        if (settings.shouldIndexSymbols) {
          val index = SymbolEmbeddingStorage.getInstance(project).index
          SymbolEmbeddingStorage.getInstance(project).registerInMemoryManager()
          SymbolEmbeddingStorage.getInstance(project).loadIndex()
          val indexSize = index.getSize()
          indexLoaded = indexLoaded && indexSize > 0
          logger.debug { "Loaded symbols embedding index from disk, size: ${indexSize}, root: ${index.root}" }
        }
      }
      listOf(filesIndexLoadingJob, classIndexLoadingJob, symbolIndexLoadingJob).joinAll()
      EmbeddingSearchLogger.indexingLoaded(project, forActions = false, TimeoutUtil.getDurationMillis(indexLoadingStartTime))
    }
  }

  private suspend fun indexProject() {
    logger.debug { "Started full project embedding indexing" }
    SEMANTIC_SEARCH_TRACER.spanBuilder(INDEXING_SPAN_NAME).useWithScope {
      startIndexingSession()
      try {
        if (isFirstIndexing) onFirstIndexingStart()
        logger.debug { "Is first indexing: ${isFirstIndexing}" }
        val projectIndexingStartTime = System.nanoTime()
        // Trigger model loading
        launch {
          serviceAsync<LocalEmbeddingServiceProvider>().getService(scheduleCleanup = false)
        }
        indexFiles(scanFiles().toList().sortedByDescending { it.name.length })
        EmbeddingSearchLogger.indexingFinished(project, forActions = false, TimeoutUtil.getDurationMillis(projectIndexingStartTime))
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
        finishIndexingSession()
      }
    }
    logger.debug { "Finished full project embedding indexing" }
  }

  private suspend fun startIndexingSession() {
    val settings = EmbeddingIndexSettingsImpl.getInstance()
    // Ensure all embedding indices are loaded into memory:
    withContext(Dispatchers.IO) {
      if (settings.shouldIndexFiles) {
        FileEmbeddingsStorage.getInstance(project).startIndexingSession()
      }
      if (settings.shouldIndexClasses) {
        ClassEmbeddingsStorage.getInstance(project).startIndexingSession()
      }
      if (settings.shouldIndexSymbols) {
        SymbolEmbeddingStorage.getInstance(project).startIndexingSession()
      }
    }
  }

  private fun finishIndexingSession() {
    val settings = EmbeddingIndexSettingsImpl.getInstance()
    if (settings.shouldIndexFiles) {
      FileEmbeddingsStorage.getInstance(project).finishIndexingSession()
    }
    if (settings.shouldIndexClasses) {
      ClassEmbeddingsStorage.getInstance(project).finishIndexingSession()
    }
    if (settings.shouldIndexSymbols) {
      SymbolEmbeddingStorage.getInstance(project).finishIndexingSession()
    }
  }

  suspend fun indexFiles(files: List<VirtualFile>, sourceType: EntitySourceType = DEFAULT) {
    val settings = EmbeddingIndexSettingsImpl.getInstance()
    if (!settings.shouldIndexAnythingFileBased) return
    startIndexingSession()
    try {
      withContext(indexingScope.coroutineContext) {
        withContext(indexingContext) {
          LocalEmbeddingServiceProvider.getInstance().indexingSession {
            suspend fun processChunk(chunk: HashMap<CharSequence, MutableList<IndexableEntity>>) {
              val orderedRepresentations = chunk.map { it.key as String }.toList()
              val embeddings = orderedRepresentations.map { embed(it).normalized() }
              // Associate embeddings with actual indexable entities again
              (orderedRepresentations.asSequence() zip embeddings.asSequence())
                .flatMap { (representation, embedding) -> chunk[representation]!!.map { it to embedding } }
                .groupBy({ (entity, _) -> getIndex(entity) }) { (entity, embedding) -> entity.id to embedding }
                .forEach { (index, values) -> index.addEntries(values) }
              chunk.clear()
            }

            val entityChannel = Channel<List<IndexableEntity>>(capacity = 4096)

            repeat(EMBEDDING_WORKER_COUNT) {
              launch {
                // The map structure of chunk is important to not calculate embeddings multiple times for the same string
                val chunk = HashMap<CharSequence, MutableList<IndexableEntity>>(BATCH_SIZE)
                for (entitySeq in entityChannel) {
                  for (entity in entitySeq) {
                    // The initial capacity of ArrayList here is selected empirically
                    chunk.getOrPut((entity.indexableRepresentation as CharSequence).take(64)) { ArrayList(2) }.add(entity)
                    // Batching and batch size accelerate the indexing speed and are crucial
                    if (chunk.size == BATCH_SIZE && EmbeddingIndexMemoryManager.getInstance().checkCanAddEntry()) {
                      processChunk(chunk)
                    }
                  }
                }
                if (chunk.isNotEmpty()) processChunk(chunk)
              }
            }

            val psiManager = PsiManager.getInstance(project)
            val processedFiles = AtomicInteger(0)
            val total: Int = filesLimit?.let { minOf(files.size, it) } ?: files.size
            logger.debug { "Effective embedding indexing files limit: $total" }
            withContext(filesIterationContext) {
              val limit = filesLimit
              repeat(FILE_WORKER_COUNT) { worker ->
                launch {
                  for ((i, file) in files.withIndex()) {
                    if (i % FILE_WORKER_COUNT != worker) continue
                    if (limit != null && processedFiles.get() >= limit) return@launch
                    if (file.isFile && file.isValid && file.isInLocalFileSystem) {
                      processFile(file, psiManager, settings, entityChannel)
                      processedFiles.incrementAndGet()
                    }
                    else {
                      logger.debug { "File is not valid: ${file.name}" }
                    }
                  }
                }
              }
            }
            entityChannel.close()
          }
        }
      }
    }
    finally {
      finishIndexingSession()
    }
    LocalEmbeddingServiceProvider.getInstance().cleanup()
  }

  private suspend fun processFile(
    file: VirtualFile, psiManager: PsiManager, settings: EmbeddingIndexSettings,
    channel: SendChannel<List<IndexableEntity>>,
  ) = coroutineScope {

    val jobs = ArrayList<Deferred<List<IndexableEntity>>>(3)
    if (settings.shouldIndexFiles) {
      jobs.add(async {
        listOf(IndexableFile(file))
      })
    }

    if (settings.shouldIndexClasses) {
      jobs.add(async {
        readActionUndispatched {
          FileIndexableEntitiesProvider.extractClasses(psiManager.findFile(file) ?: return@readActionUndispatched emptyFlow())
        }.toList()
      })
    }

    if (settings.shouldIndexSymbols) {
      jobs.add(async {
        readActionUndispatched {
          FileIndexableEntitiesProvider.extractSymbols(psiManager.findFile(file) ?: return@readActionUndispatched emptyFlow())
        }.toList()
      })
    }

    channel.send(jobs.awaitAll().flatten())
  }


  private fun getIndex(entity: IndexableEntity) = when (entity) {
    is IndexableFile -> FileEmbeddingsStorage.getInstance(project).index
    is IndexableClass -> ClassEmbeddingsStorage.getInstance(project).index
    is IndexableSymbol -> SymbolEmbeddingStorage.getInstance(project).index
    else -> throw IllegalArgumentException("Unexpected indexable entity type")
  }

  data class EntityChunkGroup(val index: DiskSynchronizedEmbeddingSearchIndex, val actionType: EntityActionType, val sourceType: EntitySourceType)

  private suspend fun processEntity(entityActions: List<EntityIndexAction>) {
    val entityMap = entityActions.associateBy { it.entity.id }
    val entityByOperations = entityMap.values.groupBy { EntityChunkGroup(getIndex(it.entity), it.actionType, it.sourceType) }

    entityByOperations.forEach { chunk ->
      when (chunk.key.actionType) {
        ADD -> EmbeddingIndexingTask.Add(chunk.value.map { it.entity.id }, chunk.value.map { it.entity.indexableRepresentation }, sourceType = chunk.key.sourceType).run(chunk.key.index)
        REMOVE -> EmbeddingIndexingTask.DeleteDiskSynchronized(chunk.value.map { it.entity.id }).run(chunk.key.index)
      }
      chunk.key.index.incrementEventBufferedCount(chunk.value.size.toLong(), entityActions[0].sourceType)
    }
  }

  private fun scanFiles(): Flow<VirtualFile> {
    val scanLimit = filesLimit?.let { it * 2 } // do not scan all files if there is a limit
    var filteredFiles = 0
    return channelFlow {
      SEMANTIC_SEARCH_TRACER.spanBuilder(SCANNING_SPAN_NAME).useWithScope {
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

  @Suppress("unused")
  fun stopIndexingIfDisabled() {
    if (!EmbeddingIndexSettingsImpl.getInstance().shouldIndexAnythingFileBased) {
      indexingScope.coroutineContext.cancelChildren()
    }
  }

  private suspend fun onFirstIndexingStart() {
    val settings = EmbeddingIndexSettingsImpl.getInstance()
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

  private fun onFirstIndexingFinish() = cs.launch {
    val indexSavingStartTime = System.nanoTime()
    val savingJobs = mutableListOf<Job>()
    val settings = EmbeddingIndexSettingsImpl.getInstance()
    if (settings.shouldIndexFiles) {
      val index = FileEmbeddingsStorage.getInstance(project).index
      index.onIndexingFinish()
      if (index.changed) {
        savingJobs.add(launch(Dispatchers.IO) { FileEmbeddingsStorage.getInstance(project).saveIndex() })
      }
    }
    if (settings.shouldIndexClasses) {
      val index = ClassEmbeddingsStorage.getInstance(project).index
      index.onIndexingFinish()
      if (index.changed) {
        savingJobs.add(launch(Dispatchers.IO) { ClassEmbeddingsStorage.getInstance(project).saveIndex() })
      }
    }
    if (settings.shouldIndexSymbols) {
      val index = SymbolEmbeddingStorage.getInstance(project).index
      index.onIndexingFinish()
      if (index.changed) {
        savingJobs.add(launch(Dispatchers.IO) { SymbolEmbeddingStorage.getInstance(project).saveIndex() })
      }
    }
    savingJobs.joinAll()
    EmbeddingSearchLogger.indexingSaved(project, forActions = false, TimeoutUtil.getDurationMillis(indexSavingStartTime))
  }

  companion object {
    fun getInstance(project: Project): FileBasedEmbeddingStoragesManager = project.service()

    private const val FILE_WORKER_COUNT = 4
    private const val EMBEDDING_WORKER_COUNT = 4
    private const val BATCH_SIZE = 256
    private const val CHANNEL_CAPACITY = 4096

    private val logger = Logger.getInstance(FileBasedEmbeddingStoragesManager::class.java)

    const val SCANNING_SPAN_NAME = "embeddingFilesScanning"
    const val INDEXING_SPAN_NAME = "embeddingIndexing"
  }
}