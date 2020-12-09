// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.*

internal class ProjectRootsChangeListener(private val project: Project) {
  fun beforeChanged(event: VersionedStorageChange) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    if (project.isDisposed) return
    val projectRootManager = ProjectRootManager.getInstance(project)
    if (projectRootManager !is ProjectRootManagerBridge) return
    val performUpdate = shouldFireRootsChanged(event, project)
    if (performUpdate) projectRootManager.rootsChanged.beforeRootsChanged()
  }

  fun changed(event: VersionedStorageChange) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    if (project.isDisposed) return
    val projectRootManager = ProjectRootManager.getInstance(project)
    if (projectRootManager !is ProjectRootManagerBridge) return
    val performUpdate = shouldFireRootsChanged(event, project)
    if (performUpdate) {
      val rootsChangeType = getRootsChangeType(event, project)
      projectRootManager.rootsChanged.rootsChanged(rootsChangeType)
    }
  }

  // TODO extract it to indexing api
  private fun getRootsChangeType(event: VersionedStorageChange, project: Project): ProjectRootManagerImpl.RootsChangeType {
    var result: ProjectRootManagerImpl.RootsChangeType? = null
    for (change in event.getAllChanges()) {
      val currentRootsChangeType: ProjectRootManagerImpl.RootsChangeType = when (change) {
        is EntityChange.Added -> {
          if (!shouldFireRootsChanged(change.entity, project)) continue
          ProjectRootManagerImpl.RootsChangeType.ROOTS_ADDED
        }
        is EntityChange.Removed -> {
          if (!shouldFireRootsChanged(change.entity, project)) continue
          ProjectRootManagerImpl.RootsChangeType.ROOTS_REMOVED
        }
        is EntityChange.Replaced -> {
          val oldEntity = change.oldEntity
          val newEntity = change.newEntity
          if (!shouldFireRootsChanged(oldEntity, project) ||
              !shouldFireRootsChanged(newEntity, project)) continue
          if (oldEntity is ModuleEntity &&
              newEntity is ModuleEntity &&
              oldEntity.dependencies.containsAll(newEntity.dependencies)) {
            continue
          }
          ProjectRootManagerImpl.RootsChangeType.GENERIC
        }
      }
      if (result == null) {
        result = currentRootsChangeType
      }
      else if (currentRootsChangeType == ProjectRootManagerImpl.RootsChangeType.GENERIC) {
        result = ProjectRootManagerImpl.RootsChangeType.GENERIC
        break
      }
      else if (currentRootsChangeType == ProjectRootManagerImpl.RootsChangeType.ROOTS_ADDED) {
        result == ProjectRootManagerImpl.RootsChangeType.ROOTS_ADDED
      }
      else if (currentRootsChangeType == ProjectRootManagerImpl.RootsChangeType.ROOTS_REMOVED &&
               result != ProjectRootManagerImpl.RootsChangeType.ROOTS_ADDED) {
        result = ProjectRootManagerImpl.RootsChangeType.ROOTS_REMOVED
      }
    }
    return result ?: ProjectRootManagerImpl.RootsChangeType.ROOTS_REMOVED
  }

  private fun shouldFireRootsChanged(events: VersionedStorageChange, project: Project): Boolean {
    return events.getAllChanges().any {
      val entity = when (it) {
        is EntityChange.Added -> it.entity
        is EntityChange.Removed -> it.entity
        is EntityChange.Replaced -> it.newEntity
      }
      shouldFireRootsChanged(entity, project)
    }
  }

  private fun shouldFireRootsChanged(entity: WorkspaceEntity,
                                     project: Project): Boolean {
    return when (entity) {
      // Library changes should not fire any events if the library is not included in any of order entries
      is LibraryEntity -> libraryHasOrderEntry(entity, project)
      is LibraryPropertiesEntity -> libraryHasOrderEntry(entity.library, project)
      is ModuleEntity, is JavaModuleSettingsEntity, is ModuleCustomImlDataEntity, is ModuleGroupPathEntity,
      is SourceRootEntity, is JavaSourceRootEntity, is JavaResourceRootEntity, is CustomSourceRootPropertiesEntity,
      is ContentRootEntity -> true
      else -> false
    }
  }

  private fun libraryHasOrderEntry(library: LibraryEntity, project: Project): Boolean {
    if (library.tableId is LibraryTableId.ModuleLibraryTableId) {
      return true
    }
    val libraryName = library.name
    ModuleManager.getInstance(project).modules.forEach { module ->
      val exists = ModuleRootManager.getInstance(module).orderEntries.any { it is LibraryOrderEntry && it.libraryName == libraryName }
      if (exists) return true
    }
    return false
  }
}