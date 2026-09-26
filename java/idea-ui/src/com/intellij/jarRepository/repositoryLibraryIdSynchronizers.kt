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
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge

internal class GlobalChangedRepositoryLibraryIdSynchronizer(private val queue: LibraryIdSynchronizationQueue,
                                                            private val disposable: Disposable)
  : LibraryTable.Listener, RootProvider.RootSetChangedListener {
  override fun beforeLibraryRemoved(library: Library) {
    if (library is LibraryBridge) {
      library.rootProvider.removeRootSetChangedListener(this)
      queue.revokeSynchronization(library.libraryId)
    }
  }

  override fun afterLibraryAdded(library: Library) {
    if (library is LibraryBridge) {
      library.rootProvider.addRootSetChangedListener(this, disposable)
      queue.requestSynchronization(library.libraryId)
    }
  }

  override fun rootSetChanged(wrapper: RootProvider) {
    for (library in LibraryTablesRegistrar.getInstance().libraryTable.libraries) {
      if (library.rootProvider == wrapper) {
        if (library is LibraryBridge) {
          queue.requestSynchronization(library.libraryId)
        }
        break
      }
    }
  }
}

internal fun installOnExistingLibraries(project: Project, listener: RootProvider.RootSetChangedListener, disposable: Disposable) {
  getGlobalAndCustomLibraryTables(project)
    .flatMap { it.libraries.asIterable() }
    .filterIsInstance<LibraryEx>()
    .forEach { it.rootProvider.addRootSetChangedListener(listener, disposable) }
}

internal fun getGlobalAndCustomLibraryTables(project: Project): Sequence<LibraryTable> {
  return LibraryTablesRegistrar.getInstance().customLibraryTables.asSequence() + LibraryTablesRegistrar.getInstance().getGlobalLibraryTable(project)
}

internal class ChangedRepositoryLibraryIdSynchronizer(private val queue: LibraryIdSynchronizationQueue) : WorkspaceModelChangeListener {
  /**
   * This is a flag indicating that the [beforeChanged] method was called.
   * Since we subscribe using the code, this may lead to IDEA-324532.
   * With this flag, we skip the "after" event if the before event wasn't called.
   */
  private var beforeCalled = false

  override fun beforeChanged(event: VersionedStorageChange) {
    beforeCalled = true
    val libraryIdsToRevoke = mutableListOf<LibraryId>()
    for (change in event.getChanges(LibraryEntity::class.java)) {
      val removed = change as? EntityChange.Removed ?: continue
      libraryIdsToRevoke.add(removed.oldEntity.symbolicId)
    }

    for (change in event.getChanges(ModuleEntity::class.java)) {
      val (oldLModuleDeps, newModuleDeps) = when (change) {
        is EntityChange.Added -> continue
        is EntityChange.Removed -> change.oldEntity.dependencies to emptyList()
        is EntityChange.Replaced -> change.oldEntity.dependencies to change.newEntity.dependencies
      }

      val newLibDeps = newModuleDeps.filterIsInstanceTo<LibraryDependency, HashSet<LibraryDependency>>(HashSet())
      oldLModuleDeps.forEach { oldDependency ->
        if (oldDependency !is LibraryDependency) return@forEach
        if (newLibDeps.contains(oldDependency)) return@forEach
        libraryIdsToRevoke.add(oldDependency.library)
      }
    }
    libraryIdsToRevoke.forEach { queue.revokeSynchronization(it) }
  }

  override fun changed(event: VersionedStorageChange) {
    if (!beforeCalled) return
    beforeCalled = false
    var libraryReloadRequested = false
    val libraryIdsToSync = mutableListOf<LibraryId>()

    for (change in event.getChanges(LibraryEntity::class.java)) {
      val entity = when (change) {
                     is EntityChange.Added -> change.newEntity
                     is EntityChange.Replaced -> change.newEntity
                     is EntityChange.Removed -> null
                   } ?: continue
      libraryIdsToSync.add(entity.symbolicId)
      libraryReloadRequested = true
    }


    for (change in event.getChanges(ModuleEntity::class.java)) {
      val (oldLModuleDeps, newModuleDeps) = when (change) {
        is EntityChange.Removed -> continue
        is EntityChange.Added -> emptyList<ModuleDependencyItem>() to change.newEntity.dependencies
        is EntityChange.Replaced -> change.oldEntity.dependencies to change.newEntity.dependencies
      }

      val oldLibDeps = oldLModuleDeps.filterIsInstanceTo<LibraryDependency, HashSet<LibraryDependency>>(HashSet())
      newModuleDeps.forEach { newDependency ->
        if (newDependency !is LibraryDependency) return@forEach
        if (oldLibDeps.contains(newDependency)) return@forEach
        libraryIdsToSync.add(newDependency.library)
        libraryReloadRequested = true
      }
    }

    libraryIdsToSync.forEach { queue.requestSynchronization(it) }
    if (libraryReloadRequested) {
      queue.flush()
    }
  }
}