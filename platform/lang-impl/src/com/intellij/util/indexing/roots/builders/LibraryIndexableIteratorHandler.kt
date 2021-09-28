// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots.builders

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.IndexableEntityProviderMethods.createIterators
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.LibraryBridgeIndexableFilesIteratorImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleDependencyItem
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity

class LibraryIndexableIteratorHandler : IndexableIteratorBuilderHandler {
  override fun accepts(builder: IndexableEntityProvider.IndexableIteratorBuilder): Boolean =
    builder is LibraryIdIteratorBuilder

  override fun instantiate(builders: Collection<IndexableEntityProvider.IndexableIteratorBuilder>,
                           project: Project,
                           entityStorage: WorkspaceEntityStorage): List<IndexableFilesIterator> {
    builders as Collection<LibraryIdIteratorBuilder>
    val idsToIndex = mutableSetOf<LibraryId>()
    builders.forEach { builder -> if (builder.dependencyChecked) idsToIndex.add(builder.libraryId) }

    val dependencyChecker = DependencyChecker(entityStorage, idsToIndex)
    builders.forEach { builder ->
      if (builder.dependencyChecked) {
        return@forEach
      }
      dependencyChecker.checkDependency(builder.libraryId)
    }

    val result = mutableListOf<IndexableFilesIterator>()
    idsToIndex.forEach { id ->
      result.addAll(createLibraryIterator(id, entityStorage, project))
    }
    return result
  }

  private fun createLibraryIterator(libraryId: LibraryId,
                                    entityStorage: WorkspaceEntityStorage,
                                    project: Project): Collection<IndexableFilesIterator> =
    if (libraryId.tableId is LibraryTableId.GlobalLibraryTableId) {
      LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(libraryId.tableId.level, project)?.let {
        it.getLibraryByName(libraryId.name)?.let { createIterators(it, libraryId) }
      } ?: emptyList()
    }
    else {
      entityStorage.resolve(libraryId)?.let { entityStorage.libraryMap.getDataByEntity(it) }?.let {
        listOf(LibraryBridgeIndexableFilesIteratorImpl(it))
      } ?: emptyList()
    }

  private class DependencyChecker(val entityStorage: WorkspaceEntityStorage,
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
        if (next is ModuleDependencyItem.Exportable.LibraryDependency) {
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