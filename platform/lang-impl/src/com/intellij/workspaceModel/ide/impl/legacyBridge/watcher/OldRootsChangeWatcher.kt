// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DuplicatedCode")

package com.intellij.workspaceModel.ide.impl.legacyBridge.watcher

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.util.indexing.EntityIndexingServiceEx
import com.intellij.util.io.URLUtil
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.GlobalLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootManagerBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.watcher.VirtualFileUrlWatcher.Companion.calculateAffectedEntities
import com.intellij.workspaceModel.ide.legacyBridge.GlobalLibraryTableBridge
import com.intellij.workspaceModel.ide.virtualFile
import com.intellij.workspaceModel.storage.EntityReference
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager

/**
 * Old implementation is temporarily kept here until the old implementation of [com.intellij.openapi.roots.ProjectFileIndex] is available.
 * This class will be deleted as soon as `platform.projectModel.workspace.model.file.index` registry option is removed. 
 */
@Service(Service.Level.PROJECT)
internal class OldRootsChangeWatcher(private val project: Project) {
  private val virtualFileManager = VirtualFileUrlManager.getInstance(project)

  internal fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
    val entityChanges = EntityChangeStorage()
    val entityStorage = WorkspaceModel.getInstance(project).currentSnapshot
    for (event in events) {
      when (event) {
        is VFileDeleteEvent ->
          calculateEntityChangesIfNeeded(entityChanges, entityStorage, virtualFileManager.fromUrl(event.file.url), true)
        is VFileCreateEvent -> {
          val parentUrl = event.parent.url
          val protocolEnd = parentUrl.indexOf(URLUtil.SCHEME_SEPARATOR)
          val url = if (protocolEnd != -1) {
            parentUrl.substring(0, protocolEnd) + URLUtil.SCHEME_SEPARATOR + event.path
          }
          else {
            VfsUtilCore.pathToUrl(event.path)
          }
          val virtualFileUrl = virtualFileManager.fromUrl(url)
          calculateEntityChangesIfNeeded(entityChanges, entityStorage, virtualFileUrl, false)
          if (url.startsWith(URLUtil.FILE_PROTOCOL) && (event.isDirectory || event.childName.endsWith(".jar"))) {
            //if a new directory or a new jar file is created, we may have roots pointing to files under it with jar protocol
            val suffix = if (event.isDirectory) "" else URLUtil.JAR_SEPARATOR
            val jarFileUrl = URLUtil.JAR_PROTOCOL + URLUtil.SCHEME_SEPARATOR + VfsUtil.urlToPath(url) + suffix
            val jarVirtualFileUrl = virtualFileManager.fromUrl(jarFileUrl)
            calculateEntityChangesIfNeeded(entityChanges, entityStorage, jarVirtualFileUrl, false)
          }
        }
        is VFileCopyEvent -> calculateEntityChangesIfNeeded(entityChanges, entityStorage,
                                                            virtualFileManager.fromUrl(VfsUtilCore.pathToUrl(event.path)), false)
        is VFilePropertyChangeEvent, is VFileMoveEvent -> {
          val (oldUrl, newUrl) = getOldAndNewUrls(event)
          if (oldUrl != newUrl) {
            calculateEntityChangesIfNeeded(entityChanges, entityStorage, virtualFileManager.fromUrl(oldUrl), true)
            calculateEntityChangesIfNeeded(entityChanges, entityStorage, virtualFileManager.fromUrl(newUrl), false)
          }
        }
      }
    }

    if (!entityChanges.hasChanges()) {
      return null
    }
    
    return object : AsyncFileListener.ChangeApplier {
      override fun beforeVfsChange() {
        fireRootsChangeEvent(true, entityChanges)
      }

      override fun afterVfsChange() {
        fireRootsChangeEvent(entityChangesStorage = entityChanges)
      }
    }
  }

  private fun getIncludingJarDirectory(storage: EntityStorage,
                                       virtualFileUrl: VirtualFileUrl): VirtualFileUrl? {
    val indexedJarDirectories = (storage.getVirtualFileUrlIndex() as VirtualFileIndex).getIndexedJarDirectories()
    var parentVirtualFileUrl: VirtualFileUrl? = virtualFileUrl
    while (parentVirtualFileUrl != null && parentVirtualFileUrl !in indexedJarDirectories) {
      parentVirtualFileUrl = virtualFileManager.getParentVirtualUrl(parentVirtualFileUrl)
    }
    return if (parentVirtualFileUrl != null && parentVirtualFileUrl in indexedJarDirectories) parentVirtualFileUrl else null
  }

  private fun calculateEntityChangesIfNeeded(entityChanges: EntityChangeStorage,
                                             storage: EntityStorage,
                                             virtualFileUrl: VirtualFileUrl,
                                             allRootsWereRemoved: Boolean) {
    val includingJarDirectory = getIncludingJarDirectory(storage, virtualFileUrl)
    if (includingJarDirectory != null) {
      val entities = storage.getVirtualFileUrlIndex().findEntitiesByUrl(includingJarDirectory).map { it.first.createReference<WorkspaceEntity>() }.toList()
      entityChanges.addAffectedEntities(entities, allRootsWereRemoved)
      return
    }

    val affectedEntities = mutableListOf<EntityWithVirtualFileUrl>()
    collectAffectedEntities(virtualFileUrl, storage, affectedEntities, allRootsWereRemoved, entityChanges)
    virtualFileUrl.subTreeFileUrls.forEach { fileUrl ->
      collectAffectedEntities(fileUrl, storage, affectedEntities, allRootsWereRemoved, entityChanges)
    }

    val indexingServiceEx = EntityIndexingServiceEx.getInstanceEx()
    if (affectedEntities.any { it.propertyName != "entitySource" && indexingServiceEx.shouldCauseRescan(it.entity, project) }) {
      entityChanges.addAffectedEntities(affectedEntities.map { it.entity.createReference() }, allRootsWereRemoved)
    }
  }

  private fun collectAffectedEntities(url: VirtualFileUrl, storage: EntityStorage, affectedEntities: MutableList<EntityWithVirtualFileUrl>,
                                      allRootsWereRemoved: Boolean, entityChanges: EntityChangeStorage) {
    val hasEntities = calculateAffectedEntities(storage, url, affectedEntities)
    if (hasEntities && allRootsWereRemoved) {
      entityChanges.addFileToInvalidate(url.virtualFile)
    }
  }

  private fun isRootChangeForbidden(): Boolean {
    if (project.isDisposed) return true
    val projectRootManager = ProjectRootManager.getInstance(project)
    if (projectRootManager !is ProjectRootManagerBridge) return true
    return projectRootManager.isFiringEvent()
  }

  private fun fireRootsChangeEvent(beforeRootsChanged: Boolean = false, entityChangesStorage: EntityChangeStorage) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    val affectedEntities = entityChangesStorage.affectedEntities
    if (WorkspaceFileIndexEx.IS_ENABLED && affectedEntities.isNotEmpty()) {
      val workspaceFileIndex = WorkspaceFileIndex.getInstance(project) as WorkspaceFileIndexEx
      if (beforeRootsChanged) {
        workspaceFileIndex.indexData.markDirty(affectedEntities, entityChangesStorage.filesToInvalidate)
      }
      else {
        workspaceFileIndex.indexData.markDirty(affectedEntities, entityChangesStorage.filesToInvalidate)
        workspaceFileIndex.indexData.updateDirtyEntities()
      }
    }
    // Keep old behaviour for global libraries
    if (GlobalLibraryTableBridge.isEnabled() && affectedEntities.isNotEmpty()) {
      val entityStorage = WorkspaceModel.getInstance(project).currentSnapshot
      val globalLibraryTableBridge = GlobalLibraryTableBridge.getInstance() as GlobalLibraryTableBridgeImpl

      affectedEntities.forEach { entityRef ->
        val libraryEntity = (entityRef.resolve(entityStorage) as? LibraryEntity) ?: return@forEach
        if (libraryEntity.tableId.level != LibraryTablesRegistrar.APPLICATION_LEVEL) return@forEach
        globalLibraryTableBridge.fireRootSetChanged(libraryEntity, entityStorage)
      }
    }

    val indexingInfo = entityChangesStorage.createIndexingInfo()
    if (indexingInfo != null && !isRootChangeForbidden()) {
      val projectRootManager = ProjectRootManager.getInstance(project) as ProjectRootManagerBridge
      if (beforeRootsChanged)
        projectRootManager.rootsChanged.beforeRootsChanged()
      else {
        projectRootManager.rootsChanged.rootsChanged(indexingInfo)
      }
    }
  }
}

private class EntityChangeStorage {
  private var entitiesToReindex: MutableList<EntityReference<WorkspaceEntity>>? = null
  val affectedEntities = HashSet<EntityReference<WorkspaceEntity>>()
  val filesToInvalidate = HashSet<VirtualFile>()
  
  fun hasChanges() = affectedEntities.isNotEmpty() || entitiesToReindex != null || filesToInvalidate.isNotEmpty() 

  private fun initChanges(): MutableList<EntityReference<WorkspaceEntity>> = entitiesToReindex ?: (mutableListOf<EntityReference<WorkspaceEntity>>().also { entitiesToReindex = it })

  fun addAffectedEntities(entities: Collection<EntityReference<WorkspaceEntity>>, allRootsWereRemoved: Boolean) {
    affectedEntities.addAll(entities)
    val toReindex = initChanges()
    if (!allRootsWereRemoved) {
      toReindex.addAll(entities)
    }
  } 
  
  fun createIndexingInfo() = entitiesToReindex?.let {
    EntityIndexingServiceEx.getInstanceEx().createWorkspaceEntitiesRootsChangedInfo(it)
  }

  fun addFileToInvalidate(file: VirtualFile?) {
    file?.let { filesToInvalidate.add(it) }
  }
}

private fun getOldAndNewUrls(event: VFileEvent): Pair<String, String> {
  return when (event) {
    is VFilePropertyChangeEvent -> VfsUtilCore.pathToUrl(event.oldPath) to VfsUtilCore.pathToUrl(event.newPath)
    is VFileMoveEvent -> VfsUtilCore.pathToUrl(event.oldPath) to VfsUtilCore.pathToUrl(event.newPath)
    else -> error("Unexpected event type: ${event.javaClass}")
  }
}
