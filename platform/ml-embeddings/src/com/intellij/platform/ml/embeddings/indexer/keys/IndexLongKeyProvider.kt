// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.keys

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.ml.embeddings.indexer.CLASS_NAME_EMBEDDING_INDEX_NAME
import com.intellij.platform.ml.embeddings.indexer.EmbeddingKey
import com.intellij.platform.ml.embeddings.indexer.FILE_NAME_EMBEDDING_INDEX_NAME
import com.intellij.platform.ml.embeddings.indexer.IndexId
import com.intellij.platform.ml.embeddings.indexer.SYMBOL_NAME_EMBEDDING_INDEX_NAME
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableEntity
import com.intellij.platform.ml.embeddings.indexer.entities.LongIndexableEntity
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.ID

@Service(Service.Level.APP)
internal class IndexLongKeyProvider : EmbeddingStorageKeyProvider<Long> {
  companion object {
    fun getInstance(): IndexLongKeyProvider = service()
  }

  override suspend fun findKey(project: Project?, indexId: IndexId, entity: IndexableEntity): Long {
    return (entity as LongIndexableEntity).longId
  }

  override suspend fun findEntityId(project: Project?, indexId: IndexId, key: Long): String? {
    val fileId = (key shr 32).toInt()
    val file = VirtualFileManager.getInstance().findFileById(fileId)
    if (file == null) {
      thisLogger().warn("Unknown fileId extracted from storage key")
      return null
    }
    val hash = key.toInt()
    var result: String? = null
    val index = getEmbeddingIndexId(indexId) ?: throw IllegalArgumentException("$indexId request is not supported")
    smartReadAction(project!!) {
      FileBasedIndex.getInstance().processValues(
        /* indexId = */ index,
        /* dataKey = */ EmbeddingKey(hash),
        /* inFile = */ file,
        /* processor = */ FileBasedIndex.ValueProcessor { _, value -> result = value; false },
        /* filter = */ GlobalSearchScope.fileScope(project, file))
    }
    if (result == null) {
      thisLogger().warn("File based index key extracted from storage key not found in file scope")
    }
    return result
  }

  private fun getEmbeddingIndexId(indexId: IndexId): ID<EmbeddingKey, String>? {
    val index = when (indexId) {
      IndexId.FILES -> FILE_NAME_EMBEDDING_INDEX_NAME
      IndexId.CLASSES -> CLASS_NAME_EMBEDDING_INDEX_NAME
      IndexId.SYMBOLS -> SYMBOL_NAME_EMBEDDING_INDEX_NAME
      else -> null
    }
    return index
  }
}
