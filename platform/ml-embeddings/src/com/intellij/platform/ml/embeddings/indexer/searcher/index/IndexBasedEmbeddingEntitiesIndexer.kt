// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.searcher.index

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.platform.ml.embeddings.indexer.*
import com.intellij.platform.ml.embeddings.indexer.entities.*
import com.intellij.platform.ml.embeddings.indexer.searcher.EmbeddingEntitiesIndexer
import com.intellij.platform.ml.embeddings.jvm.indices.EntityId
import com.intellij.platform.ml.embeddings.settings.EmbeddingIndexSettings
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

internal class IndexBasedEmbeddingEntitiesIndexer(cs: CoroutineScope) : EmbeddingEntitiesIndexer {
  @OptIn(ExperimentalCoroutinesApi::class)
  private val indexingScope = cs.childScope("IndexBasedEmbeddingEntitiesIndexer indexing scope", Dispatchers.Default.limitedParallelism(TOTAL_THREAD_LIMIT_FOR_INDEXING))

  override suspend fun index(project: Project, settings: EmbeddingIndexSettings) {
    indexingScope.launch {
      extractAndAddEntities(project) { filesChannel, classesChannel, symbolsChannel ->
        coroutineScope {
          if (filesChannel != null) launchFetchingEntities(FILE_NAME_EMBEDDING_INDEX_NAME, filesChannel, project) { entityId -> IndexableFile(entityId) }
          if (classesChannel != null) launchFetchingEntities(CLASS_NAME_EMBEDDING_INDEX_NAME, classesChannel, project) { entityId -> IndexableClass(entityId) }
          if (symbolsChannel != null) launchFetchingEntities(SYMBOL_NAME_EMBEDDING_INDEX_NAME, symbolsChannel, project) { entityId -> IndexableSymbol(entityId) }
        }
      }
    }.join()
  }

  private fun CoroutineScope.launchFetchingEntities(
    index: ID<EmbeddingKey, String>,
    channel: Channel<IndexableEntity>,
    project: Project,
    toIndexableEntity: (EntityId) -> IndexableEntity,
  ) {
    launch {
      fetchEntities(index, channel, project) { key, name ->
        LongIndexableEntity(key, toIndexableEntity(EntityId(name)))
      }
      channel.close()
    }
  }

  private suspend fun fetchEntities(
    indexId: ID<EmbeddingKey, String>,
    channel: Channel<IndexableEntity>,
    project: Project,
    nameToEntity: (Long, String) -> LongIndexableEntity,
  ) {
    val fileBasedIndex = FileBasedIndex.getInstance()
    val scope = GlobalSearchScope.projectScope(project)
    val keys = smartReadAction(project) { fileBasedIndex.getAllKeys(indexId, project) }

    val chunkSize = Registry.intValue("intellij.platform.ml.embeddings.file.based.index.processing.chunk.size")
    keys.asSequence().chunked(chunkSize).forEach { chunk ->
      val fileIdsAndNames = smartReadAction(project) {
        chunk.mapNotNull { key ->
          var result: Pair<Long, String>? = null
          // TODO: besides entity name (id) we should access indexable representation here
          //  (e.g. via TextRange in index values) because vectors are calculated from it
          fileBasedIndex.processValues(indexId, key, null, { virtualFile, name ->
            if (virtualFile is VirtualFileWithId) {
              result = Pair(key.toLong(virtualFile.id), name)
            }
            false // we don't support the situation when there are two or more different symbols within one file with the same hash
          }, scope)
          result
        }
      }
      for ((id, name) in fileIdsAndNames) {
        channel.send(nameToEntity(id, name))
      }
    }
  }

  override fun dispose() = Unit
}