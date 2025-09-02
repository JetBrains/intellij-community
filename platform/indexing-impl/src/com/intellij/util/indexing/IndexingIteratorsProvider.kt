// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.indexing.roots.IndexableFilesIterator
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface IndexingIteratorsProvider {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): IndexingIteratorsProvider {
      return project.service<IndexingIteratorsProvider>()
    }
  }

  @RequiresBackgroundThread
  fun getIndexingIterators(): List<IndexableFilesIterator>

  @RequiresBackgroundThread
  fun shouldBeIndexed(file: VirtualFile): Boolean

  @RequiresBackgroundThread
  fun getModuleIndexingIterators(entity: ModuleEntity, entityStorage: EntityStorage): Collection<IndexableFilesIterator>

}
