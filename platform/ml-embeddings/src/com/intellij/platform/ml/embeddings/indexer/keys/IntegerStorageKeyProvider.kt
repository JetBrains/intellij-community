// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.indexer.keys

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.platform.ml.embeddings.external.artifacts.LocalArtifactsManager
import com.intellij.platform.ml.embeddings.indexer.IndexId
import com.intellij.platform.ml.embeddings.indexer.entities.IndexableEntity
import com.intellij.util.io.PersistentStringEnumerator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Files
import kotlin.io.path.div

@Service(Service.Level.APP)
class IntegerStorageKeyProvider : EmbeddingStorageKeyProvider<Long>, Disposable {
  private val enumerators = mutableMapOf<IndexPath, PersistentStringEnumerator>()
  private val mutex = Mutex()

  override suspend fun findKey(project: Project?, indexId: IndexId, entity: IndexableEntity): Long {
    return getEnumerator(project, indexId).enumerate(entity.id.id + "$$$" + entity.indexableRepresentation).toLong()
  }

  override suspend fun findEntityId(project: Project?, indexId: IndexId, key: Long): String? {
    return getEnumerator(project, indexId).valueOf(key.toInt())?.split("$$$", limit = 2)?.first() ?: ""
  }

  private suspend fun getEnumerator(project: Project?, indexId: IndexId): PersistentStringEnumerator {
    val path = IndexPath(project, indexId)
    return mutex.withLock {
      enumerators.getOrPut(path) {
        var projectIndexRoot = LocalArtifactsManager.indicesRoot
        if (project != null) projectIndexRoot /= project.getProjectCacheFileName()
        projectIndexRoot /= indexId.toString()
        val enumeratorRoot = (projectIndexRoot / ENUMERATOR_FOLDER / ENUMERATOR_FILE).also { Files.createDirectories(it.parent) }
        PersistentStringEnumerator(enumeratorRoot, 1024 * 1024, true, null)
      }
    }
  }

  override fun dispose() {
    runBlockingMaybeCancellable {
      mutex.withLock {
        for (enumerator in enumerators.values) {
          enumerator.close()
        }
      }
    }
  }

  companion object {
    private const val ENUMERATOR_FOLDER = "enumerator"
    private const val ENUMERATOR_FILE = "ids.enum"

    fun getInstance(): IntegerStorageKeyProvider = service()
  }
}

data class IndexPath(val project: Project?, val indexId: IndexId)