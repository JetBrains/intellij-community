// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.util.indexing.EntityIndexingServiceEx
import com.intellij.util.indexing.IndexableFilesIndex
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.IndexableFilesIndexImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleDependencyIndexImpl
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex
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
    (ModuleDependencyIndex.getInstance(project) as ModuleDependencyIndexImpl).workspaceModelChanged(event);
    if (IndexableFilesIndex.shouldBeUsed()) {
      IndexableFilesIndexImpl.getInstanceImpl(project).workspaceModelChanged(event)
    }
    val projectRootManager = ProjectRootManager.getInstance(project)
    if (projectRootManager !is ProjectRootManagerBridge) return
    val performUpdate = shouldFireRootsChanged(event, project)
    if (performUpdate) {
      val rootsChangeInfo = EntityIndexingServiceEx.getInstanceEx().createWorkspaceChangedEventInfo(event.getAllChanges().toList())
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
        // Library changes should not fire any events if no modules depend on it
        is LibraryEntity -> hasDependencyOn(entity, project)
        is LibraryPropertiesEntity -> hasDependencyOn(entity.library, project)
        is ModuleGroupPathEntity, is CustomSourceRootPropertiesEntity -> true
        is ExcludeUrlEntity -> {
          val library = entity.library
          val contentRoot = entity.contentRoot
          if (library != null) {
            hasDependencyOn(library, project)
          }
          else if (contentRoot != null) {
            val entityClass = contentRoot::class.java
            customEntitiesToFireRootsChanged.find { it.isAssignableFrom(entityClass) } != null
          }
          else {
            false
          }
        }
        else -> {
          val entityClass = entity::class.java
          customEntitiesToFireRootsChanged.find { it.isAssignableFrom(entityClass) } != null
        }
      }
    }

    private fun hasDependencyOn(library: LibraryEntity, project: Project): Boolean {
      return ModuleDependencyIndex.getInstance(project).hasDependencyOn(library.symbolicId)
    }
  }
}