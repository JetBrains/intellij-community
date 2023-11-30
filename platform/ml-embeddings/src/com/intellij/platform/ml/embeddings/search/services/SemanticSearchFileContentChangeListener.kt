// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.platform.ml.embeddings.search.indices.IndexableEntity
import com.intellij.util.containers.CollectionFactory
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

abstract class SemanticSearchFileContentChangeListener<E : IndexableEntity>(val project: Project) {
  private val fileIdToEntityCounts: MutableMap<VirtualFilePointer, Array<Pair<String, Int>>> = CollectionFactory.createSmallMemoryFootprintMap()
  private val mutex = ReentrantLock()
  abstract val isEnabled: Boolean

  abstract fun getStorage(): FileContentBasedEmbeddingsStorage<E>
  abstract fun getEntity(id: String): E

  fun addEntityCountsForFile(file: VirtualFile, symbols: List<E>) = mutex.withLock {
    fileIdToEntityCounts[getFileId(file)] = symbols.groupingBy { it.id.intern() }.eachCount().toList().toTypedArray()
  }

  private fun getFileId(file: VirtualFile) = VirtualFilePointerManager.getInstance().create(file, getStorage(), null)

  internal fun inferEntityDiff(file: VirtualFile, entities: List<IndexableEntity>) = mutex.withLock {
    val entityCounts = entities.groupingBy { it.id.intern() }.eachCount()
    val fileId = getFileId(file)
    val oldEntityCounts = fileIdToEntityCounts[fileId] ?: emptyArray()
    for ((entityId, count) in entityCounts) {
      val oldCount = oldEntityCounts.find { it.first == entityId }?.second ?: 0
      if (count > oldCount) {
        getStorage().run { repeat(count - oldCount) { addEntity(getEntity(entityId)) } }
      }
    }
    for ((entityId, oldCount) in oldEntityCounts) {
      val count = entityCounts.getOrDefault(entityId, 0)
      if (oldCount > count) {
        getStorage().run { repeat(oldCount - count) { deleteEntity(getEntity(entityId)) } }
      }
    }
    if (entityCounts.isEmpty()) {
      fileIdToEntityCounts.remove(fileId)
    }
    else {
      fileIdToEntityCounts[fileId] = entityCounts.toList().toTypedArray()
    }
  }
}