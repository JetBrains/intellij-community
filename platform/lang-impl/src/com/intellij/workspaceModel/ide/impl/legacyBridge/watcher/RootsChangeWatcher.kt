// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.watcher

import com.google.common.io.Files
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.getModuleNameByFilePath
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl
import com.intellij.openapi.roots.impl.storage.ClasspathStorage
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.util.containers.ContainerUtil
import com.intellij.project.stateStore
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.io.URLUtil
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.legacyBridge.watcher.VirtualFileUrlWatcher.Companion.calculateAffectedEntities
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootManagerBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootsChangeListener.Companion.calculateRootsChangeType
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootsChangeListener.Companion.shouldFireRootsChanged
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ModifiableModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

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
      @Volatile
      private var result: ProjectRootManagerImpl.RootsChangeType? = null
      private val changedUrlsList = ContainerUtil.createConcurrentList<Pair<String, String>>()
      private val changedModuleStorePaths = ContainerUtil.createConcurrentList<Pair<Module, Path>>()

      override fun prepareChange(events: MutableList<out VFileEvent>): AsyncFileListener.ChangeApplier {
        result = null
        changedUrlsList.clear()
        changedModuleStorePaths.clear()

        val entityStorage = WorkspaceModel.getInstance(project).entityStorage.current
        events.forEach { event ->
          when (event) {
            is VFileDeleteEvent -> calculateRootsChangeTypeIfNeeded(entityStorage, virtualFileManager.fromUrl(event.file.url),
                                                                    ProjectRootManagerImpl.RootsChangeType.ROOTS_REMOVED)
            is VFileCreateEvent -> {
              val parentUrl = event.parent.url
              val protocolEnd = parentUrl.indexOf(URLUtil.SCHEME_SEPARATOR)
              val url = if (protocolEnd != -1) {
                // For .jar files we should change schema from "file://" to "jar://"
                if (event.path.endsWith(".jar")) {
                  URLUtil.JAR_PROTOCOL + URLUtil.SCHEME_SEPARATOR + event.path + URLUtil.JAR_SEPARATOR
                }
                else {
                  parentUrl.substring(0, protocolEnd) + URLUtil.SCHEME_SEPARATOR + event.path
                }
              } else {
                VfsUtilCore.pathToUrl(event.path)
              }
              val virtualFileUrl = virtualFileManager.fromUrl(url)
              calculateRootsChangeTypeIfNeeded(entityStorage, virtualFileUrl, ProjectRootManagerImpl.RootsChangeType.ROOTS_ADDED)
            }
            is VFileCopyEvent -> calculateRootsChangeTypeIfNeeded(entityStorage,
                                                                  virtualFileManager.fromUrl(VfsUtilCore.pathToUrl(event.path)),
                                                                  ProjectRootManagerImpl.RootsChangeType.ROOTS_ADDED)
            is VFilePropertyChangeEvent, is VFileMoveEvent -> {
              if (event is VFilePropertyChangeEvent) propertyChanged(event)
              val (oldUrl, newUrl) = getUrls(event) ?: return@forEach
              if (oldUrl != newUrl) {
                calculateRootsChangeTypeIfNeeded(entityStorage, virtualFileManager.fromUrl(oldUrl), ProjectRootManagerImpl.RootsChangeType.GENERIC)
                calculateRootsChangeTypeIfNeeded(entityStorage, virtualFileManager.fromUrl(newUrl), ProjectRootManagerImpl.RootsChangeType.GENERIC)
                changedUrlsList.add(Pair(oldUrl, newUrl))
              }
            }
          }
        }

        return object : AsyncFileListener.ChangeApplier {
          override fun beforeVfsChange() {
            changedUrlsList.forEach { (oldUrl, newUrl) -> virtualFileUrlWatcher.onVfsChange(oldUrl, newUrl) }
            fireRootsChangeEvent(true)
          }

          override fun afterVfsChange() {
            changedUrlsList.forEach { (oldUrl, newUrl) -> updateModuleName(oldUrl, newUrl) }
            changedModuleStorePaths.forEach { (module, path) ->
              module.stateStore.setPath(path)
              ClasspathStorage.modulePathChanged(module)
            }
            if (changedModuleStorePaths.isNotEmpty()) moduleManager.incModificationCount()
            fireRootsChangeEvent()
          }
        }
      }

      fun isUnderJarDirectory(storage: WorkspaceEntityStorage, virtualFileUrl: VirtualFileUrl): Boolean {
        val indexedJarDirectories = (storage.getVirtualFileUrlIndex() as VirtualFileIndex).getIndexedJarDirectories()
        var parentVirtualFileUrl: VirtualFileUrl? = virtualFileUrl
        while (parentVirtualFileUrl != null && parentVirtualFileUrl !in indexedJarDirectories) {
          parentVirtualFileUrl = virtualFileManager.getParentVirtualUrl(parentVirtualFileUrl)
        }
        return parentVirtualFileUrl != null && parentVirtualFileUrl in indexedJarDirectories
      }

      fun calculateRootsChangeTypeIfNeeded(storage: WorkspaceEntityStorage, virtualFileUrl: VirtualFileUrl,
                                           currentRootsChangeType: ProjectRootManagerImpl.RootsChangeType) {
        if (result == ProjectRootManagerImpl.RootsChangeType.GENERIC) return
        if (isUnderJarDirectory(storage, virtualFileUrl)) {
          result = calculateRootsChangeType(result, currentRootsChangeType)
          return
        }
        val affectedEntities = mutableListOf<EntityWithVirtualFileUrl>()
        calculateAffectedEntities(storage, virtualFileUrl, affectedEntities)
        virtualFileUrl.subTreeFileUrls.forEach { fileUrl -> calculateAffectedEntities(storage, fileUrl, affectedEntities) }
        if (affectedEntities.none { shouldFireRootsChanged(it.entity, project) } && virtualFileUrl.url !in projectFilePaths) return
        result = calculateRootsChangeType(result, currentRootsChangeType)
      }

      private fun isRootChangeForbidden(): Boolean {
        if (project.isDisposed) return true
        val projectRootManager = ProjectRootManager.getInstance(project)
        if (projectRootManager !is ProjectRootManagerBridge) return true
        return projectRootManager.isFiringEvent()
      }

      private fun fireRootsChangeEvent(beforeRootsChanged: Boolean = false) {
        ApplicationManager.getApplication().assertWriteAccessAllowed()
        if (result != null && !isRootChangeForbidden()) {
          val projectRootManager = ProjectRootManager.getInstance(project) as ProjectRootManagerBridge
          if (beforeRootsChanged)
            projectRootManager.rootsChanged.beforeRootsChanged()
          else
            projectRootManager.rootsChanged.rootsChanged(result!!)
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
          diff.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) { this.name = newModuleName }
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
    @JvmStatic
    fun getInstance(project: Project): RootsChangeWatcher = project.getComponent(RootsChangeWatcher::class.java)
  }
}
