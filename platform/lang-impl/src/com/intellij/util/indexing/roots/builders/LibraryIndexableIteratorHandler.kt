// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.builders

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.LibraryIndexableFilesIterator
import com.intellij.util.indexing.roots.LibraryIndexableFilesIteratorImpl
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge

class LibraryIndexableIteratorHandler : IndexableIteratorBuilderHandler {
  override fun accepts(builder: IndexableEntityProvider.IndexableIteratorBuilder): Boolean =
    builder is LibraryIdIteratorBuilder

  override fun instantiate(builders: Collection<IndexableEntityProvider.IndexableIteratorBuilder>,
                           project: Project,
                           entityStorage: EntityStorage): List<IndexableFilesIterator> {
    @Suppress("UNCHECKED_CAST")
    builders as Collection<LibraryIdIteratorBuilder>
    val rootMap = mutableMapOf<LibraryId, Root>()
    val idsToIndex = mutableSetOf<LibraryId>()
    builders.forEach { builder ->
      val libraryId = builder.libraryId
      if (builder.dependencyChecked) {
        idsToIndex.add(libraryId)
      }
      rootMap[libraryId] = merge(getRoot(builder), rootMap[libraryId])
    }

    val dependencyChecker = DependencyChecker(entityStorage, idsToIndex)
    builders.forEach { builder ->
      if (builder.dependencyChecked) {
        return@forEach
      }
      dependencyChecker.checkDependency(builder.libraryId)
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

  private class DependencyChecker(val entityStorage: EntityStorage,
                                  val idsToIndex: MutableSet<LibraryId>) {
    private val idsFromDependencies = mutableSetOf<LibraryId>()
    private var moduleIterator: Iterator<ModuleEntity>? = null
    private var dependencyIterator: Iterator<ModuleDependencyItem>? = null

    init {
      idsFromDependencies.addAll(idsToIndex)
    }

    fun checkDependency(libraryId: LibraryId) {
      if (libraryId.tableId is LibraryTableId.ModuleLibraryTableId) {
        idsToIndex.add(libraryId)
        return
      }

      if (idsFromDependencies.contains(libraryId)) {
        idsToIndex.add(libraryId)
        return
      }

      val localModuleIterator = moduleIterator ?: entityStorage.entities(ModuleEntity::class.java).iterator()
      var localDependencyIterator = dependencyIterator

      if (localDependencyIterator == null || !localDependencyIterator.hasNext()) {
        if (localModuleIterator.hasNext()) {
          localDependencyIterator = localModuleIterator.next().dependencies.iterator()
        }
        else {
          return
        }
      }

      val foundInDependencies = checkDependencies(localDependencyIterator, libraryId)
      dependencyIterator = localDependencyIterator
      if (foundInDependencies) {
        return
      }

      while (localModuleIterator.hasNext()) {
        localDependencyIterator = localModuleIterator.next().dependencies.iterator()
        val foundInDependency = checkDependencies(localDependencyIterator, libraryId)
        dependencyIterator = localDependencyIterator
        moduleIterator = localModuleIterator
        if (foundInDependency) {
          return
        }
      }
    }

    private fun checkDependencies(iterator: Iterator<ModuleDependencyItem>, libraryId: LibraryId): Boolean {
      while (iterator.hasNext()) {
        val next = iterator.next()
        if (next is LibraryDependency) {
          idsFromDependencies.add(next.library)
          if (libraryId == next.library) {
            idsToIndex.add(libraryId)
            return true
          }
        }
      }
      return false
    }
  }
}