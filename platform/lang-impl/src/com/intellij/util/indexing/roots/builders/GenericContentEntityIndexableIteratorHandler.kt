// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.builders

import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.util.indexing.roots.GenericContentEntityIteratorImpl
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.origin.MutableIndexingUrlRootHolder

class GenericContentEntityIndexableIteratorHandler : IndexableIteratorBuilderHandler {
  override fun accepts(builder: IndexableEntityProvider.IndexableIteratorBuilder): Boolean {
    return builder is GenericContentEntityBuilder<*>
  }

  override fun instantiate(builders: Collection<IndexableEntityProvider.IndexableIteratorBuilder>,
                           project: Project,
                           entityStorage: EntityStorage): List<IndexableFilesIterator> {
    @Suppress("UNCHECKED_CAST")
    builders as Collection<GenericContentEntityBuilder<WorkspaceEntity>>

    return builders.groupBy { it.entityPointer }.mapNotNull { entry ->
      entry.value.fold(MutableIndexingUrlRootHolder()) { holder, builder ->
        holder.addRoots(builder.roots)
        return@fold holder
      }.toRootHolder().let { if (it.isEmpty()) null else GenericContentEntityIteratorImpl(entry.key, it, entry.value[0].presentation) }
    }
  }
}