// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap

@Deprecated("Remove after the `workspace.model.custom.library.bridge` registry key removal")
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
}

@Deprecated("Remove after the `workspace.model.custom.library.bridge` registry key removal")
internal class ChangedRepositoryLibrarySynchronizer(private val project: Project,
                                                    private val queue: LibrarySynchronizationQueue) : WorkspaceModelChangeListener {
  /**
   * This is a flag indicating that the [beforeChanged] method was called.
   * Since we subscribe using the code, this may lead to IDEA-324532.
   * With this flag, we skip the "after" event if the before event wasn't called.
   */
  private var beforeCalled = false

  override fun beforeChanged(event: VersionedStorageChange) {
    beforeCalled = true
    for (change in event.getChanges(LibraryEntity::class.java)) {
      val removed = change as? EntityChange.Removed ?: continue
      val library = findLibrary(removed.entity.symbolicId, event.storageBefore)
      if (library != null) {
        queue.revokeSynchronization(library)
      }
    }

    for (change in event.getChanges(ModuleEntity::class.java)) {
      val (oldLModuleDeps, newModuleDeps) = when (change) {
        is EntityChange.Added -> continue
        is EntityChange.Removed -> change.entity.dependencies to emptyList()
        is EntityChange.Replaced -> change.oldEntity.dependencies to change.newEntity.dependencies
      }

      val newLibDeps = newModuleDeps.filterIsInstanceTo<LibraryDependency, HashSet<LibraryDependency>>(HashSet())
      oldLModuleDeps.forEach { oldDependency ->
        if (oldDependency !is LibraryDependency) return@forEach
        if (newLibDeps.contains(oldDependency)) return@forEach
        val library = findLibrary(oldDependency, event.storageBefore) ?: return@forEach
        queue.revokeSynchronization(library)
      }
    }
  }

  override fun changed(event: VersionedStorageChange) {
    if (!beforeCalled) return
    beforeCalled = false
    var libraryReloadRequested = false

    for (change in event.getChanges(LibraryEntity::class.java)) {
      val entity = when (change) {
                     is EntityChange.Added -> change.entity
                     is EntityChange.Replaced -> change.newEntity
                     is EntityChange.Removed -> null
                   } ?: continue
      val library = findLibrary(entity.symbolicId, event.storageAfter)
      if (library != null) {
        queue.requestSynchronization(library)
        libraryReloadRequested = true
      }
    }


    for (change in event.getChanges(ModuleEntity::class.java)) {
      val (oldLModuleDeps, newModuleDeps) = when (change) {
        is EntityChange.Removed -> continue
        is EntityChange.Added -> emptyList<ModuleDependencyItem>() to change.entity.dependencies
        is EntityChange.Replaced -> change.oldEntity.dependencies to change.newEntity.dependencies
      }

      val oldLibDeps = oldLModuleDeps.filterIsInstanceTo<LibraryDependency, HashSet<LibraryDependency>>(HashSet())
      newModuleDeps.forEach { newDependency ->
        if (newDependency !is LibraryDependency) return@forEach
        if (oldLibDeps.contains(newDependency)) return@forEach
        val library = findLibrary(newDependency, event.storageAfter) ?: return@forEach
        queue.requestSynchronization(library)
        libraryReloadRequested = true
      }
    }

    if (libraryReloadRequested) {
      queue.flush()
    }
  }

  private fun findLibrary(libraryId: LibraryId, storage: EntityStorage): LibraryEx? {
    val library = when (val tableId = libraryId.tableId) {
      is LibraryTableId.GlobalLibraryTableId ->
        LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(tableId.level, project)?.getLibraryByName(libraryId.name)
      else -> libraryId.resolve(storage)?.let { storage.libraryMap.getDataByEntity(it) }
    }
    return library as? LibraryEx
  }

  private fun findLibrary(libDep: LibraryDependency, storage: EntityStorage): LibraryEx? =
    findLibrary(libDep.library, storage)
}