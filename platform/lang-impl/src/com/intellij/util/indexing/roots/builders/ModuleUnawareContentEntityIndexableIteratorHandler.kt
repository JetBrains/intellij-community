// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.builders

import com.intellij.openapi.project.Project
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.ModuleUnawareContentEntityIteratorImpl
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity

class ModuleUnawareContentEntityIndexableIteratorHandler : IndexableIteratorBuilderHandler {
  override fun accepts(builder: IndexableEntityProvider.IndexableIteratorBuilder): Boolean {
    return builder is ModuleUnawareContentEntityBuilder<*>
  }

  override fun instantiate(builders: Collection<IndexableEntityProvider.IndexableIteratorBuilder>,
                           project: Project,
                           entityStorage: EntityStorage): List<IndexableFilesIterator> {
    @Suppress("UNCHECKED_CAST")
    builders as Collection<ModuleUnawareContentEntityBuilder<WorkspaceEntity>>

    val (custom, usual) = builders.partition { it.customization != null }

    val customIterators = custom.flatMap {
      it.customization!!.createModuleUnawareContentIterators(it.entityReference, it.roots)
    }

    val usualIterators = usual.groupBy { it.entityReference }.map {
      ModuleUnawareContentEntityIteratorImpl(it.key, it.value.flatMap { builder -> builder.roots })
    }

    return usualIterators + customIterators
  }
}