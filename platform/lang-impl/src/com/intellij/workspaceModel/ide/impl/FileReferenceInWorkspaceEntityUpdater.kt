// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.google.common.io.Files
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.storage.ClasspathStorage
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.jps.serialization.impl.ModulePath
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.util.PathUtil
import com.intellij.workspaceModel.core.fileIndex.impl.getOldAndNewUrls
import com.intellij.workspaceModel.ide.impl.legacyBridge.watcher.VirtualFileUrlWatcher
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path

/**
 * This class is used to update entities in [WorkspaceModel] when files referenced from them are moved or renamed.
 */
internal class FileReferenceInWorkspaceEntityUpdater(private val project: Project) {
  internal fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
    val changedUrlsList = ArrayList<Pair<String, String>>()
    val changedModuleStorePaths = ArrayList<Pair<Module, Path>>()

    for (event in events) {
      when (event) {
        is VFilePropertyChangeEvent, is VFileMoveEvent -> {
          if (event is VFilePropertyChangeEvent) {
            collectChangedModuleStorePathsAfterDirectoryRename(event, changedModuleStorePaths)
          }
          if (event is VFileMoveEvent) {
            addChangedModuleStorePathAfterImlFileMove(event, changedModuleStorePaths)
          }
          val (oldUrl, newUrl) = getOldAndNewUrls(event)
          if (oldUrl != newUrl) {
            changedUrlsList.add(Pair(oldUrl, newUrl))
          }
        }
      }
    }

    if (changedUrlsList.isEmpty() && changedModuleStorePaths.isEmpty()) {
      return null
    }

    return object : AsyncFileListener.ChangeApplier {
      override fun beforeVfsChange() {
        val virtualFileUrlWatcher = VirtualFileUrlWatcher.getInstance(project)
        changedUrlsList.forEach { (oldUrl, newUrl) -> virtualFileUrlWatcher.onVfsChange(oldUrl, newUrl) }
      }

      override fun afterVfsChange() {
        changedUrlsList.forEach { (oldUrl, newUrl) -> updateModuleName(oldUrl, newUrl) }
        changedModuleStorePaths.forEach { (module, path) ->
          module.stateStore.setPath(path)
          ClasspathStorage.modulePathChanged(module)
        }
        if (changedModuleStorePaths.isNotEmpty()) ModuleManager.getInstance(project).incModificationCount()
      }
    }
  }

  private fun updateModuleName(oldUrl: String, newUrl: String) {
    if (!oldUrl.isImlFile() || !newUrl.isImlFile()) return
    val oldModuleName = ModulePath.getModuleNameByFilePath(oldUrl)
    val newModuleName = ModulePath.getModuleNameByFilePath(newUrl)
    if (oldModuleName == newModuleName) return

    val oldModuleId = ModuleId(oldModuleName)
    val workspaceModel = WorkspaceModel.getInstance(project)

    val moduleEntity = workspaceModel.currentSnapshot.resolve(oldModuleId)
    val description = "Update module name when iml file is renamed"
    if (moduleEntity != null) {
      val oldModuleDir = PathUtil.getParentPath(oldUrl)
      if (moduleEntity.entitySource.virtualFileUrl?.url != oldModuleDir)
        return
      workspaceModel.updateProjectModel(description) { diff ->
        diff.modifyModuleEntity(moduleEntity) { this.name = newModuleName }
      }
    }
    val unloadedModule = (workspaceModel as WorkspaceModelInternal).currentSnapshotOfUnloadedEntities.resolve(oldModuleId)
    if (unloadedModule != null) {
      (workspaceModel as WorkspaceModelInternal).updateUnloadedEntities(description) { diff ->
        diff.modifyModuleEntity(unloadedModule) { this.name = newModuleName }
      }
    }
  }

  private fun collectChangedModuleStorePathsAfterDirectoryRename(event: VFilePropertyChangeEvent, changedModuleStorePaths: ArrayList<Pair<Module, Path>>) {
    if (!event.file.isDirectory || event.requestor is StateStorage || event.propertyName != VirtualFile.PROP_NAME) return

    val parentPath = event.file.parent?.path ?: return
    val newAncestorPath = "$parentPath/${event.newValue}"
    val oldAncestorPath = "$parentPath/${event.oldValue}"
    for (module in ModuleManager.getInstance(project).modules) {
      if (!module.isLoaded || module.isDisposed) continue

      val moduleFilePath = module.moduleFilePath
      if (FileUtil.isAncestor(oldAncestorPath, moduleFilePath, true)) {
        changedModuleStorePaths.add(
          Pair(module, Paths.get(newAncestorPath, FileUtil.getRelativePath(oldAncestorPath, moduleFilePath, '/'))))
      }
    }
  }

  private fun addChangedModuleStorePathAfterImlFileMove(event: VFileMoveEvent, changedModuleStorePaths: MutableList<Pair<Module, Path>>) {
    if (!event.file.isFile || event.requestor is StateStorage || event.file.extension != ModuleFileType.DEFAULT_EXTENSION) return
    val module = ModuleManager.getInstance(project).findModuleByName(event.file.nameWithoutExtension) ?: return

    if (module.isLoaded && !module.isDisposed && module.moduleFilePath == event.oldPath) {
      changedModuleStorePaths.add(Pair(module, Path(event.newPath)))
    }
  }

  private fun String.isImlFile() = Files.getFileExtension(this) == ModuleFileType.DEFAULT_EXTENSION
}  
