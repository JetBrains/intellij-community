// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.watcher

import com.google.common.io.Files
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.service
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.getModuleNameByFilePath
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.storage.ClasspathStorage
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.project.stateStore
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.indexing.EntityIndexingServiceEx
import com.intellij.util.io.URLUtil
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootManagerBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootsChangeListener.Companion.shouldFireRootsChanged
import com.intellij.workspaceModel.ide.impl.legacyBridge.watcher.VirtualFileUrlWatcher.Companion.calculateAffectedEntities
import com.intellij.workspaceModel.ide.impl.virtualFile
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.bridgeEntities.modifyEntity
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import java.nio.file.Path
import java.nio.file.Paths

private val LOG = logger<RootsChangeWatcher>()

/**
 * Provides rootsChanged events if roots validity was changed.
 */
@Service(Service.Level.PROJECT)
private class RootsChangeWatcher(private val project: Project) {
  private val moduleManager by lazy(LazyThreadSafetyMode.NONE) { ModuleManager.getInstance(project) }
  private val projectFilePaths = CollectionFactory.createFilePathSet()
  private val virtualFileManager = VirtualFileUrlManager.getInstance(project)
  private val virtualFileUrlWatcher = VirtualFileUrlWatcher.getInstance(project)

  private val changedUrlsList = ContainerUtil.createConcurrentList<Pair<String, String>>()
  private val changedModuleStorePaths = ContainerUtil.createConcurrentList<Pair<Module, Path>>()

  init {
    val store = project.stateStore
    val projectFilePath = store.projectFilePath
    if (Project.DIRECTORY_STORE_FOLDER != projectFilePath.parent.fileName?.toString()) {
      projectFilePaths.add(VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(projectFilePath.toString())))
      projectFilePaths.add(VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(store.workspacePath.toString())))
    }
  }

  class RootsChangeWatcherDelegator : AsyncFileListener {
    override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
      val appliers = ProjectManager.getInstance().openProjects.mapNotNull {
        try {
          it.service<RootsChangeWatcher>().prepareChange(events)
        }
        catch (ignore: AlreadyDisposedException) {
          // ignore disposed project
          null
        }
      }

      return when {
        appliers.isEmpty() -> null
        appliers.size == 1 -> appliers.first()
        else -> {
          object : AsyncFileListener.ChangeApplier {
            override fun beforeVfsChange() {
              for (applier in appliers) {
                applier.beforeVfsChange()
              }
            }

            override fun afterVfsChange() {
              for (applier in appliers) {
                applier.afterVfsChange()
              }
            }
          }
        }
      }
    }
  }

  private fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier {
    val entityChanges = EntityChangeStorage()
    changedUrlsList.clear()
    changedModuleStorePaths.clear()

    val entityStorage = WorkspaceModel.getInstance(project).entityStorage.current
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
          if (event is VFilePropertyChangeEvent) propertyChanged(event)
          val (oldUrl, newUrl) = getUrls(event) ?: continue
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
      val entities = storage.getVirtualFileUrlIndex().findEntitiesByUrl(includingJarDirectory).map { it.first }.toList()
      entityChanges.addAffectedEntities(entities, allRootsWereRemoved)
      return
    }

    val affectedEntities = mutableListOf<EntityWithVirtualFileUrl>()
    calculateAffectedEntities(storage, virtualFileUrl, affectedEntities)
    if (allRootsWereRemoved) {
      entityChanges.addFileToInvalidate(virtualFileUrl.virtualFile)
    }
    virtualFileUrl.subTreeFileUrls.forEach { fileUrl -> 
      if (calculateAffectedEntities(storage, fileUrl, affectedEntities) && allRootsWereRemoved) {
        entityChanges.addFileToInvalidate(fileUrl.virtualFile)
      }
    }

    if (affectedEntities.any { it.propertyName != "entitySource" && shouldFireRootsChanged(it.entity, project) }
        || virtualFileUrl.url in projectFilePaths) {
      entityChanges.addAffectedEntities(affectedEntities.map { it.entity }, allRootsWereRemoved)
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
        workspaceFileIndex.markDirty(affectedEntities, entityChangesStorage.filesToInvalidate)
      }
      else {
        workspaceFileIndex.markDirty(affectedEntities, entityChangesStorage.filesToInvalidate)
        workspaceFileIndex.updateDirtyEntities()
      }
    }
    val indexingInfo = entityChangesStorage.createIndexingInfo()
    if (indexingInfo != null && !isRootChangeForbidden()) {
      val projectRootManager = ProjectRootManager.getInstance(project) as ProjectRootManagerBridge
      if (beforeRootsChanged)
        projectRootManager.rootsChanged.beforeRootsChanged()
      else {
        if (LOG.isTraceEnabled) {
          LOG.trace("Roots changed: changed urls = $changedUrlsList, changed module store paths = $changedModuleStorePaths")
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
    workspaceModel.updateProjectModel("Update module name") { diff ->
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
}

private class EntityChangeStorage {
  private var entitiesToReindex: MutableList<WorkspaceEntity>? = null
  val affectedEntities = HashSet<WorkspaceEntity>()
  val filesToInvalidate = HashSet<VirtualFile>()

  private fun initChanges(): MutableList<WorkspaceEntity> = entitiesToReindex ?: (mutableListOf<WorkspaceEntity>().also { entitiesToReindex = it })

  fun addAffectedEntities(entities: Collection<WorkspaceEntity>, allRootsWereRemoved: Boolean) {
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