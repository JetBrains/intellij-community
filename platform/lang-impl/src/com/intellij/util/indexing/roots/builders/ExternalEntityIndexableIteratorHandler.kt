// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.builders

import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.util.indexing.roots.ExternalEntityIndexableIteratorImpl
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.origin.MutableIndexingSourceRootHolder

class ExternalEntityIndexableIteratorHandler : IndexableIteratorBuilderHandler {
  override fun accepts(builder: IndexableEntityProvider.IndexableIteratorBuilder): Boolean {
    return builder is ExternalEntityIteratorBuilder<*>
  }

  override fun instantiate(builders: Collection<IndexableEntityProvider.IndexableIteratorBuilder>,
                           project: Project,
                           entityStorage: EntityStorage): List<IndexableFilesIterator> {
    @Suppress("UNCHECKED_CAST")
    builders as Collection<ExternalEntityIteratorBuilder<WorkspaceEntity>>

    return builders.groupBy { it.entityReference }.map {
      val sum = it.value.map { builder -> builder.roots }.foldRight(MutableIndexingSourceRootHolder()) { holder, mutableHolder ->
        mutableHolder.addRoots(holder)
        return@foldRight mutableHolder
      }
      ExternalEntityIndexableIteratorImpl(it.key, sum, it.value[0].presentation)
    }
  }
}