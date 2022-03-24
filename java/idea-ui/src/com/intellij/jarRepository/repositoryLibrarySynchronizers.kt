// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.*
import java.util.function.Consumer

internal class GlobalChangedRepositoryLibrarySynchronizer(private val queue: LibrarySynchronizationQueue,
                                                          private val disposable: Disposable)
  : LibraryTable.Listener, RootProvider.RootSetChangedListener {
  override fun beforeLibraryRemoved(library: Library) {
    if (library is LibraryEx) {
      library.rootProvider.removeRootSetChangedListener(this)
      queue.revokeSynchronization(library)
    }
  }

  override fun afterLibraryAdded(library: Library) {
    if (library is LibraryEx) {
      library.rootProvider.addRootSetChangedListener(this, disposable)
      queue.requestSynchronization(library)
    }
  }

  override fun rootSetChanged(wrapper: RootProvider) {
    for (library in LibraryTablesRegistrar.getInstance().libraryTable.libraries) {
      if (library.rootProvider == wrapper) {
        if (library is LibraryEx) {
          queue.requestSynchronization(library)
        }
        break
      }
    }
  }

  fun installOnExistingLibraries() = getGlobalAndCustomLibraryTables()
    .flatMap { it.libraries.asIterable() }
    .filterIsInstance<LibraryEx>()
    .forEach { it.rootProvider.addRootSetChangedListener(this, disposable) }

  companion object {
    @JvmStatic
    fun getGlobalAndCustomLibraryTables(): List<LibraryTable> {
      return LibraryTablesRegistrar.getInstance().customLibraryTables + LibraryTablesRegistrar.getInstance().libraryTable
    }
  }
}

internal class ChangedRepositoryLibrarySynchronizer(private val project: Project,
                                                    private val queue: LibrarySynchronizationQueue) : WorkspaceModelChangeListener {
  override fun beforeChanged(event: VersionedStorageChange) {
    for (change in event.getChanges(LibraryEntity::class.java)) {
      val removed = change as? EntityChange.Removed ?: continue
      val library = findLibrary(removed.entity.persistentId(), event.storageBefore)
      if (library != null) {
        queue.revokeSynchronization(library)
      }
    }

    for (change in event.getChanges(ModuleEntity::class.java)) {
      val (oldLibDeps, newLibDeps) = when (change) {
        is EntityChange.Added -> continue
        is EntityChange.Removed -> change.entity.libraryDependencies() to emptySet()
        is EntityChange.Replaced -> change.oldEntity.libraryDependencies() to change.newEntity.libraryDependencies()
      }

      oldLibDeps
        .filterNot { newLibDeps.contains(it) }
        .mapNotNull { findLibrary(it, event.storageBefore) }
        .forEach { queue.revokeSynchronization(it) }
    }
  }

  override fun changed(event: VersionedStorageChange) {
    var libraryReloadRequested = false

    for (change in event.getChanges(LibraryEntity::class.java)) {
      val entity = when (change) {
                     is EntityChange.Added -> change.entity
                     is EntityChange.Replaced -> change.newEntity
                     is EntityChange.Removed -> null
                   } ?: continue
      val library = findLibrary(entity.persistentId(), event.storageAfter)
      if (library != null) {
        queue.requestSynchronization(library)
        libraryReloadRequested = true
      }
    }


    for (change in event.getChanges(ModuleEntity::class.java)) {
      val (oldLibDeps, newLibDeps) = when (change) {
        is EntityChange.Removed -> continue
        is EntityChange.Added -> emptySet<ModuleDependencyItem.Exportable.LibraryDependency>() to change.entity.libraryDependencies()
        is EntityChange.Replaced -> change.oldEntity.libraryDependencies() to change.newEntity.libraryDependencies()
      }

      newLibDeps.filterNot { oldLibDeps.contains(it) }.mapNotNull { findLibrary(it, event.storageAfter) }.forEach {
        queue.requestSynchronization(it)
        libraryReloadRequested = true
      }
    }

    if (libraryReloadRequested) {
      queue.flush()
    }
  }

  private fun findLibrary(libraryId: LibraryId, storage: WorkspaceEntityStorage): LibraryEx? {
    val library = when (val tableId = libraryId.tableId) {
      is LibraryTableId.GlobalLibraryTableId ->
        LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(tableId.level, project)?.getLibraryByName(libraryId.name)
      else -> libraryId.resolve(storage)?.let { storage.libraryMap.getDataByEntity(it) }
    }
    return library as? LibraryEx
  }

  private fun findLibrary(libDep: ModuleDependencyItem.Exportable.LibraryDependency, storage: WorkspaceEntityStorage): LibraryEx? =
    findLibrary(libDep.library, storage)

  private fun ModuleEntity.libraryDependencies(): Set<ModuleDependencyItem.Exportable.LibraryDependency> =
    dependencies.filterIsInstance<ModuleDependencyItem.Exportable.LibraryDependency>().toHashSet()
}