// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.*

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
      val rootsChangeInfo = WorkspaceEventRescanningInfo(event.getAllChanges().toList(), true)
      projectRootManager.rootsChanged.rootsChanged(rootsChangeInfo)
    }
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

  companion object {
    @Volatile
    private var customEntitiesToFireRootsChanged: Set<Class<out WorkspaceEntity>>

    init {
      fun isAddable(entityClass: Class<out WorkspaceEntity>) = !LibraryEntity::class.java.isAssignableFrom(entityClass) &&
                                                               !LibraryPropertiesEntity::class.java.isAssignableFrom(entityClass)

      fun createCustomEntityClasses() =
        java.util.Set.copyOf(IndexableEntityProvider.EP_NAME.extensionList.map { it.entityClass }.filter {
          isAddable(it)
        })

      customEntitiesToFireRootsChanged = createCustomEntityClasses()

      val listener = object : ExtensionPointListener<IndexableEntityProvider<out WorkspaceEntity>> {
        override fun extensionAdded(extension: IndexableEntityProvider<out WorkspaceEntity>, pluginDescriptor: PluginDescriptor) {
          reinit(extension)
        }

        override fun extensionRemoved(extension: IndexableEntityProvider<out WorkspaceEntity>, pluginDescriptor: PluginDescriptor) {
          reinit(extension)
        }

        private fun reinit(extension: IndexableEntityProvider<out WorkspaceEntity>) {
          val entityClass = extension.entityClass
          if (isAddable(entityClass)) {
            customEntitiesToFireRootsChanged = createCustomEntityClasses()
          }
        }
      }
      IndexableEntityProvider.EP_NAME.addExtensionPointListener(listener)
    }

    internal fun shouldFireRootsChanged(entity: WorkspaceEntity, project: Project): Boolean {
      return when (entity) {
        // Library changes should not fire any events if the library is not included in any of order entries
        is LibraryEntity -> libraryHasOrderEntry(entity, project)
        is LibraryPropertiesEntity -> libraryHasOrderEntry(entity.library, project)
        is ModuleGroupPathEntity, is CustomSourceRootPropertiesEntity -> true
        else -> {
          val entityClass = entity::class.java
          customEntitiesToFireRootsChanged.find { it.isAssignableFrom(entityClass) } != null
        }
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

  class WorkspaceEventRescanningInfo(val events: List<EntityChange<*>>,
                                     val isFromWorkspaceModelEvent: Boolean) : RootsChangeRescanningInfo
}