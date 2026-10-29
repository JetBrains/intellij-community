// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.CommonBundle
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.lang.LangBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPathRoot
import com.intellij.openapi.project.projectsDataDir
import com.intellij.openapi.ui.Messages
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectEntitiesAttacher
import com.intellij.projectImport.ProjectOpenedCallback
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheSerializer
import com.intellij.workspaceModel.ide.legacyBridge.findModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.exists

private val LOG = logger<WorkspaceModelCacheAttachProcessor>()

@ApiStatus.Internal
@InternalIgnoreDependencyViolation
class WorkspaceModelCacheAttachProcessor : ProjectAttachProcessor() {
  override fun isEnabled(project: Project?, projectDir: Path?, newProject: Project?): Boolean {
    if (projectDir == null) return true
    val projectWsmCachePath = getProjectDataPathRoot(projectDir).resolve(WorkspaceModelCacheImpl.DATA_DIR_NAME)
    val cacheFile = projectWsmCachePath.resolve("cache.data")
    return cacheFile.exists()
  }

  override suspend fun attachToProjectAsync(
    project: Project,
    projectDir: Path,
    callback: ProjectOpenedCallback?,
    beforeOpen: (suspend (Project) -> Boolean)?
  ): Boolean {
    val newModules = try {
      findMainModuleInSystemDir(project, projectDir)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      LOG.error(e)
      withContext(Dispatchers.EDT) {
        Messages.showErrorDialog(project,
                                 LangBundle.message("module.attach.dialog.message.cannot.attach.project", e.message),
                                 CommonBundle.getErrorTitle())
      }
      return false
    }

    LifecycleUsageTriggerCollector.onProjectModuleAttached(project)

    if (newModules != null) {
      if (newModules.isNotEmpty()) {
        withContext(Dispatchers.EDT) {
          addPrimaryModuleDependency(project, newModules[0])
          callback?.projectOpened(project, newModules[0])
        }
      }
      return true
    }

    return false
  }

  /**
   * Imports the entities of the project in [projectDir] from its workspace model cache.
   *
   * @return the imported modules, the module with [projectDir] as a content root first,
   * or `null` if the cache does not exist or cannot be loaded.
   */
  private suspend fun findMainModuleInSystemDir(project: Project, projectDir: Path): List<Module>? {
    val projectWsmCachePath = getProjectDataPathRoot(projectDir).resolve(WorkspaceModelCacheImpl.DATA_DIR_NAME)
    val cacheFile = projectWsmCachePath.resolve("cache.data")
    if (!cacheFile.exists()) {
      return null
    }
    val serializer = WorkspaceModelCacheSerializer(project.workspaceModel.getVirtualFileUrlManager(), null)
    val invalidateCachesMarkerFile: Path = projectsDataDir.resolve(".invalidate")
    val invalidateProjectCacheMarkerFile = projectWsmCachePath.resolve(".invalidate")
    val storage = serializer.loadCacheFromFile(cacheFile, invalidateCachesMarkerFile, invalidateProjectCacheMarkerFile)
    if (storage == null) {
      return null
    }
    val toMigrate = ProjectEntitiesAttacher.getAllEntitiesToMigrate(storage)
    val attachedModuleNames = toMigrate.entities(ModuleEntity::class.java).map { it.name }.toSet()
    project.workspaceModel.update("Importing workspace model from $projectDir to project name=${project.name}, locationHash=${project.locationHash}") {
      it.applyChangesFrom(toMigrate)
    }
    val snapshot = project.workspaceModel.currentSnapshot
    val projectDirUrl = projectDir.toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())
    return snapshot.entities(ModuleEntity::class.java)
      .filter { it.name in attachedModuleNames }
      .sortedByDescending { module -> module.contentRoots.any { it.url == projectDirUrl } }
      .mapNotNull { it.findModule(snapshot) }
      .toList()
  }
}
