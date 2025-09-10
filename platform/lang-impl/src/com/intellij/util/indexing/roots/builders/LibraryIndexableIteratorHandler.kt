// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.builders

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.LibraryIndexableFilesIterator
import com.intellij.util.indexing.roots.LibraryIndexableFilesIteratorImpl
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex

class LibraryIndexableIteratorHandler : IndexableIteratorBuilderHandler {
  override fun accepts(builder: IndexableEntityProvider.IndexableIteratorBuilder): Boolean =
    !Registry.`is`("use.workspace.file.index.for.partial.scanning") &&
    builder is LibraryIdIteratorBuilder

  override fun instantiate(builders: Collection<IndexableEntityProvider.IndexableIteratorBuilder>,
                           project: Project,
                           entityStorage: EntityStorage): List<IndexableFilesIterator> {
    @Suppress("UNCHECKED_CAST")
    builders as Collection<LibraryIdIteratorBuilder>
    val rootMap = mutableMapOf<LibraryId, Root>()
    val idsToIndex = mutableSetOf<LibraryId>()
    val moduleDependencyIndex = ModuleDependencyIndex.getInstance(project)
    builders.forEach { builder ->
      val libraryId = builder.libraryId
      if (builder.dependencyChecked || moduleDependencyIndex.hasDependencyOn(libraryId)) {
        idsToIndex.add(libraryId)
      }
      rootMap[libraryId] = merge(getRoot(builder), rootMap[libraryId])
    }

    val result = mutableListOf<IndexableFilesIterator>()
    val ids = mutableSetOf<LibraryOrigin>()
    idsToIndex.sortedBy { it.toString() }.forEach { id ->
      createLibraryIterator(id, rootMap[id]!!, entityStorage, project)?.also {
        if (ids.add(it.origin)) {
          result.add(it)
        }
      }
    }
    return result
  }

  private fun merge(first: Root, second: Root?): Root {
    return when (second) {
      AllRoots -> return AllRoots
      null -> first
      is RootList -> when (first) {
        AllRoots -> AllRoots
        is RootList -> {
          first.roots.addAll(second.roots)
          first.sourceRoots.addAll(second.sourceRoots)
          first
        }
      }
    }
  }

  private fun getRoot(builder: LibraryIdIteratorBuilder): Root {
    if (builder.roots == null && builder.sourceRoots == null && builder.rootUrls == null) return AllRoots
    return RootList(builder)
  }

  private fun createLibraryIterator(libraryId: LibraryId,
                                    root: Root,
                                    entityStorage: EntityStorage,
                                    project: Project): LibraryIndexableFilesIterator? {
    return libraryId.findLibraryBridge(entityStorage, project)?.let {
      ReadAction.nonBlocking<LibraryIndexableFilesIterator?> {
        when (root) {
          AllRoots -> LibraryIndexableFilesIteratorImpl.createIterator(it)
          is RootList -> LibraryIndexableFilesIteratorImpl.createIterator(it, root.roots, root.sourceRoots)
        }
      }.executeSynchronously()
    }
  }

  private sealed interface Root

  private data object AllRoots : Root

  private class RootList() : Root {
    val roots = mutableListOf<VirtualFile>()
    val sourceRoots = mutableListOf<VirtualFile>()

    constructor(builder: LibraryIdIteratorBuilder) : this() {
      builder.roots?.also { roots.addAll(it) }
      builder.sourceRoots?.also { sourceRoots.addAll(it) }
      builder.rootUrls?.toSourceRootHolder()?.also { roots.addAll(it.roots); sourceRoots.addAll(it.sourceRoots) }
    }
  }
}