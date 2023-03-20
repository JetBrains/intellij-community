// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.builders

import com.intellij.openapi.project.Project
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.IndexableSetContributorFilesIterator
import com.intellij.workspaceModel.storage.EntityStorage

class IndexableSetContributorIndexableIteratorHandler : IndexableIteratorBuilderHandler {
  override fun accepts(builder: IndexableEntityProvider.IndexableIteratorBuilder): Boolean =
    builder is IndexableSetContributorFilesIteratorBuilder

  override fun instantiate(builders: Collection<IndexableEntityProvider.IndexableIteratorBuilder>,
                           project: Project,
                           entityStorage: EntityStorage): List<IndexableFilesIterator> {
    @Suppress("UNCHECKED_CAST")
    return (builders as Collection<IndexableSetContributorFilesIteratorBuilder>).map {
      IndexableSetContributorFilesIterator(it.name, it.debugName, it.projectAware, it.providedRootsToIndex, it.contributor)
    }
  }
}