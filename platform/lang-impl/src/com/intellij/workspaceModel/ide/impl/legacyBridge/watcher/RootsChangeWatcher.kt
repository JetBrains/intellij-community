// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.watcher

import com.google.common.io.Files
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.getModuleNameByFilePath
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.storage.ClasspathStorage
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.project.stateStore
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.URLUtil
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootManagerBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootsChangeListener
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootsChangeListener.Companion.shouldFireRootsChanged
import com.intellij.workspaceModel.ide.impl.legacyBridge.watcher.VirtualFileUrlWatcher.Companion.calculateAffectedEntities
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleId
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus
import com.intellij.workspaceModel.storage.bridgeEntities.api.modifyEntity
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Provides rootsChanged events if roots validity was changed.
 */
@ApiStatus.Internal
internal class RootsChangeWatcher(val project: Project) {
  private val moduleManager = ModuleManager.getInstance(project)
  private val projectFilePaths = CollectionFactory.createFilePathSet()
  private val virtualFileManager = VirtualFileUrlManager.getInstance(project)
  private val virtualFileUrlWatcher = VirtualFileUrlWatcher.getInstance(project)

  init {
    val store: IProjectStore = project.stateStore
    val projectFilePath = store.projectFilePath
    if (Project.DIRECTORY_STORE_FOLDER != projectFilePath.parent.fileName?.toString()) {
      projectFilePaths.add(VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(projectFilePath.toString())))
      projectFilePaths.add(VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(store.workspacePath.toString())))
    }

    VirtualFileManager.getInstance().addAsyncFileListener(object : AsyncFileListener {
      private val changedUrlsList = ContainerUtil.createConcurrentList<Pair<String, String>>()
      private val changedModuleStorePaths = ContainerUtil.createConcurrentList<Pair<Module, Path>>()

      override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier {
        val entityChanges = EntityChangeStorage()
        changedUrlsList.clear()
        changedModuleStorePaths.clear()

        val entityStorage = WorkspaceModel.getInstance(project).entityStorage.current
        events.forEach { event ->
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
              if (event is VFilePropertyChangeEvent) propertyChanged(event)
              val (oldUrl, newUrl) = getUrls(event) ?: return@forEach
              if (oldUrl != newUrl) {
                calculateEntityChangesIfNeeded(entityChanges, entityStorage, virtualFileManager.fromUrl(oldUrl), true)
                calculateEntityChangesIfNeeded(entityChanges, entityStorage, virtualFileManager.fromUrl(newUrl), false)
                changedUrlsList.add(Pair(oldUrl, newUrl))
              }
            }
          }
        }

        return object : AsyncFileListener.ChangeApplier {
          override fun beforeVfsChange() {
            changedUrlsList.forEach { (oldUrl, newUrl) -> virtualFileUrlWatcher.onVfsChange(oldUrl, newUrl) }
            fireRootsChangeEvent(true, entityChanges)
          }

          override fun afterVfsChange() {
            changedUrlsList.forEach { (oldUrl, newUrl) -> updateModuleName(oldUrl, newUrl) }
            changedModuleStorePaths.forEach { (module, path) ->
              module.stateStore.setPath(path)
              ClasspathStorage.modulePathChanged(module)
            }
            if (changedModuleStorePaths.isNotEmpty()) moduleManager.incModificationCount()
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
          val entities = if (allRootsWereRemoved) emptyList()
          else
            storage.getVirtualFileUrlIndex().findEntitiesByUrl(includingJarDirectory).toList()
          entityChanges.addAll(entities.map { pair -> pair.first })
          return
        }

        val affectedEntities = mutableListOf<EntityWithVirtualFileUrl>()
        calculateAffectedEntities(storage, virtualFileUrl, affectedEntities)
        virtualFileUrl.subTreeFileUrls.forEach { fileUrl -> calculateAffectedEntities(storage, fileUrl, affectedEntities) }

        if (affectedEntities.any { it.propertyName != "entitySource" && shouldFireRootsChanged(it.entity, project) }
            || virtualFileUrl.url in projectFilePaths) {
          val changes = if (allRootsWereRemoved) emptyList() else affectedEntities.map { it.entity }
          entityChanges.addAll(changes)
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
        val indexingInfo = entityChangesStorage.createIndexingInfo()
        if (indexingInfo != null && !isRootChangeForbidden()) {
          val projectRootManager = ProjectRootManager.getInstance(project) as ProjectRootManagerBridge
          if (beforeRootsChanged)
            projectRootManager.rootsChanged.beforeRootsChanged()
          else {
            if (log.isTraceEnabled) {
              log.trace("Roots changed: changed urls = $changedUrlsList, changed module store paths = $changedModuleStorePaths")
            }
            projectRootManager.rootsChanged.rootsChanged(indexingInfo)
          }
        }
      }

      private fun updateModuleName(oldUrl: String, newUrl: String) {
        if (!oldUrl.isImlFile() || !newUrl.isImlFile()) return
        val oldModuleName = getModuleNameByFilePath(oldUrl)
        val newModuleName = getModuleNameByFilePath(newUrl)
        if (oldModuleName == newModuleName) return

        val workspaceModel = WorkspaceModel.getInstance(project)
        val moduleEntity = workspaceModel.entityStorage.current.resolve(ModuleId(oldModuleName)) ?: return
        workspaceModel.updateProjectModel { diff ->
          diff.modifyEntity(moduleEntity) { this.name = newModuleName }
        }
      }

      private fun propertyChanged(event: VFilePropertyChangeEvent) {
        if (!event.file.isDirectory || event.requestor is StateStorage || event.propertyName != VirtualFile.PROP_NAME) return

        val parentPath = event.file.parent?.path ?: return
        val newAncestorPath = "$parentPath/${event.newValue}"
        val oldAncestorPath = "$parentPath/${event.oldValue}"
        for (module in moduleManager.modules) {
          if (!module.isLoaded || module.isDisposed) continue

          val moduleFilePath = module.moduleFilePath
          if (FileUtil.isAncestor(oldAncestorPath, moduleFilePath, true)) {
            changedModuleStorePaths.add(
              Pair(module, Paths.get(newAncestorPath, FileUtil.getRelativePath(oldAncestorPath, moduleFilePath, '/'))))
          }
        }
      }

      private fun String.isImlFile() = Files.getFileExtension(this) == ModuleFileType.DEFAULT_EXTENSION

      /** Update stored urls after folder movement */
      private fun getUrls(event: VFileEvent): Pair<String, String>? {
        val oldUrl: String
        val newUrl: String
        when (event) {
          is VFilePropertyChangeEvent -> {
            oldUrl = VfsUtilCore.pathToUrl(event.oldPath)
            newUrl = VfsUtilCore.pathToUrl(event.newPath)
          }
          is VFileMoveEvent -> {
            oldUrl = VfsUtilCore.pathToUrl(event.oldPath)
            newUrl = VfsUtilCore.pathToUrl(event.newPath)
          }
          else -> return null
        }
        return oldUrl to newUrl
      }
    }, project)
  }

  companion object {
    private val log = logger<RootsChangeWatcher>()

    @JvmStatic
    fun getInstance(project: Project): RootsChangeWatcher = project.getComponent(RootsChangeWatcher::class.java)

    private class EntityChangeStorage {
      private var entities: MutableList<WorkspaceEntity>? = null

      private fun initChanges(): MutableList<WorkspaceEntity> = entities ?: (mutableListOf<WorkspaceEntity>().also { entities = it })

      fun addAll(addedEntities: Collection<WorkspaceEntity>) {
        initChanges().addAll(addedEntities)
      }

      fun createIndexingInfo() = entities?.let { it ->
        ProjectRootsChangeListener.WorkspaceEventRescanningInfo(it.map { entity -> EntityChange.Added(entity) }, false)
      }
    }
  }
}
