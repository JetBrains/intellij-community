// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.builders

import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.SyntheticLibraryIndexableFilesIteratorImpl

class SyntheticLibraryIndexableIteratorHandler : IndexableIteratorBuilderHandler {
  override fun accepts(builder: IndexableEntityProvider.IndexableIteratorBuilder): Boolean =
    builder is SyntheticLibraryIteratorBuilder

  override fun instantiate(builders: Collection<IndexableEntityProvider.IndexableIteratorBuilder>,
                           project: Project,
                           entityStorage: EntityStorage): List<IndexableFilesIterator> {
    @Suppress("UNCHECKED_CAST")
    return (builders as Collection<SyntheticLibraryIteratorBuilder>).map {
      SyntheticLibraryIndexableFilesIteratorImpl(it.name, it.syntheticLibrary, it.roots)
    }
  }
}