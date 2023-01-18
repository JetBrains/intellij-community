// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.builders

import com.intellij.openapi.project.Project
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.ModuleUnawareContentEntityIteratorImpl
import com.intellij.workspaceModel.storage.EntityStorage

class ModuleUnawareContentEntityIndexableIteratorHandler : IndexableIteratorBuilderHandler {
  override fun accepts(builder: IndexableEntityProvider.IndexableIteratorBuilder): Boolean {
    return builder is ModuleUnawareContentEntityBuilder
  }

  override fun instantiate(builders: Collection<IndexableEntityProvider.IndexableIteratorBuilder>,
                           project: Project,
                           entityStorage: EntityStorage): List<IndexableFilesIterator> {
    @Suppress("UNCHECKED_CAST")
    builders as Collection<ModuleUnawareContentEntityBuilder>

    return builders.groupBy { it.entityReference }.map {
      ModuleUnawareContentEntityIteratorImpl(it.key, it.value.flatMap { builder -> builder.roots })
    }
  }
}