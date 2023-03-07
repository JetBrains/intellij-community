// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.builders

import com.intellij.openapi.project.Project
import com.intellij.util.indexing.roots.ExternalEntityIndexableIteratorImpl
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.workspaceModel.storage.EntityStorage

class ExternalEntityIndexableIteratorHandler : IndexableIteratorBuilderHandler {
  override fun accepts(builder: IndexableEntityProvider.IndexableIteratorBuilder): Boolean {
    return builder is ExternalEntityIteratorBuilder
  }

  override fun instantiate(builders: Collection<IndexableEntityProvider.IndexableIteratorBuilder>,
                           project: Project,
                           entityStorage: EntityStorage): List<IndexableFilesIterator> {
    @Suppress("UNCHECKED_CAST")
    builders as Collection<ExternalEntityIteratorBuilder>

    return builders.groupBy { it.entityReference }.map {
      ExternalEntityIndexableIteratorImpl(it.key, it.value.flatMap { builder -> builder.roots },
                                          it.value.flatMap { builder -> builder.sourceRoots })
    }
  }
}