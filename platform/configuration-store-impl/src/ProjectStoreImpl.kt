// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectStoreFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.util.io.delete
import com.intellij.util.io.write
import com.intellij.workspaceModel.ide.getJpsProjectConfigLocation
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsFileContentReaderWithCache
import com.intellij.workspaceModel.ide.impl.jps.serialization.ProjectStoreWithJpsContentReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.AccessDeniedException
import java.nio.file.Path
import java.util.*
import kotlin.io.path.isDirectory

@ApiStatus.Internal
open class ProjectStoreImpl(project: Project) : ProjectStoreBase(project) {
  private var lastSavedProjectName: String? = null
  protected val moduleSavingCustomizer: ModuleSavingCustomizer = ProjectStoreBridge(project)

  init {
    assert(!project.isDefault)
  }

  override val serviceContainer: ComponentManagerImpl
    get() = project as ComponentManagerImpl

  final override fun getPathMacroManagerForDefaults() = PathMacroManager.getInstance(project)

  override val storageManager = ProjectStateStorageManager(TrackingPathMacroSubstitutorImpl(PathMacroManager.getInstance(project)), project)

  override fun setPath(path: Path) {
    setPath(file = path, isRefreshVfsNeeded = true, template = null)
  }

  override fun getProjectName(): String {
    if (!isDirectoryBased) {
      return storageManager.expandMacro(PROJECT_FILE).fileName.toString().removeSuffix(ProjectFileType.DOT_DEFAULT_EXTENSION)
    }

    val projectDir = directoryStorePath!!
    val storedName = JpsPathUtil.readProjectName(projectDir)
    if (storedName != null) {
      lastSavedProjectName = storedName
      return storedName
    }

    return JpsPathUtil.getDefaultProjectName(projectDir)
  }

  private suspend fun saveProjectName() {
    if (!isDirectoryBased) {
      return
    }

    val currentProjectName = project.name
    if (lastSavedProjectName == currentProjectName) {
      return
    }

    lastSavedProjectName = currentProjectName

    val basePath = projectBasePath

    fun doSave() {
      if (currentProjectName == basePath.fileName?.toString()) {
        // name equals to base path name - just remove name
        getNameFile().delete()
      }
      else if (basePath.isDirectory()) {
        getNameFile().write(currentProjectName.toByteArray())
      }
    }

    try {
      doSave()
    }
    catch (e: AccessDeniedException) {
      val status = ensureFilesWritable(project, listOf(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(getNameFile())!!))
      if (status.hasReadonlyFiles()) {
        throw e
      }

      doSave()
    }
  }

  final override suspend fun doSave(saveResult: SaveResult, forceSavingAllSettings: Boolean) {
    coroutineScope {
      launch {
        // save modules before the project
        val saveSessions = Collections.synchronizedList(ArrayList<SaveSession>())
        val projectSessionManager = createSaveSessionProducerManager()
        saveModules(saveSessions, saveResult, forceSavingAllSettings, projectSessionManager)
        saveSettingsSavingComponentsAndCommitComponents(saveResult, forceSavingAllSettings, projectSessionManager)
        projectSessionManager.collectSaveSessions(saveSessions)
        if (saveSessions.isNotEmpty()) {
          projectSessionManager.saveAndValidate(saveSessions, saveResult)
        }
      }

      launch {
        try {
          saveProjectName()
        }
        catch (e: Throwable) {
          LOG.error("Unable to store project name", e)
        }
      }
    }
  }

  protected open suspend fun saveModules(
    saveSessions: MutableList<SaveSession>,
    saveResult: SaveResult,
    forceSavingAllSettings: Boolean,
    projectSessionManager: ProjectSaveSessionProducerManager
  ) { }

  final override fun createSaveSessionProducerManager(): ProjectSaveSessionProducerManager {
    return moduleSavingCustomizer.createSaveSessionProducerManager()
  }

  final override fun commitObsoleteComponents(session: SaveSessionProducerManager, isProjectLevel: Boolean) {
    if (isDirectoryBased) {
      super.commitObsoleteComponents(session, true)
    }
  }
}

@ApiStatus.Internal
interface ModuleSavingCustomizer {
  fun createSaveSessionProducerManager(): ProjectSaveSessionProducerManager
  fun saveModules(projectSaveSessionManager: SaveSessionProducerManager, store: IProjectStore)
  fun commitModuleComponents(projectSaveSessionManager: SaveSessionProducerManager,
                             moduleStore: ComponentStoreImpl,
                             moduleSaveSessionManager: SaveSessionProducerManager)
}

@ApiStatus.Internal
open class ProjectWithModuleStoreImpl(project: Project) : ProjectStoreImpl(project), ProjectStoreWithJpsContentReader {
  final override suspend fun saveModules(
    saveSessions: MutableList<SaveSession>,
    saveResult: SaveResult,
    forceSavingAllSettings: Boolean,
    projectSessionManager: ProjectSaveSessionProducerManager
  ) {
    moduleSavingCustomizer.saveModules(projectSessionManager, this)
    for (module in ModuleManager.getInstance(project).modules) {
      val moduleStore = module.getService(IComponentStore::class.java) as? ComponentStoreImpl ?: continue
      val moduleSessionManager = moduleStore.createSaveSessionProducerManager()
      moduleStore.commitComponents(forceSavingAllSettings, moduleSessionManager, saveResult)
      moduleSavingCustomizer.commitModuleComponents(projectSessionManager, moduleStore, moduleSessionManager)
      moduleSessionManager.collectSaveSessions(saveSessions)
    }
  }

  override fun createContentReader(): JpsFileContentReaderWithCache {
    return StorageJpsConfigurationReader(project, getJpsProjectConfigLocation(project)!!)
  }

}

abstract class ProjectStoreFactoryImpl : ProjectStoreFactory {
  final override fun createDefaultProjectStore(project: Project): IComponentStore = DefaultProjectStoreImpl(project)
}

internal class PlatformLangProjectStoreFactory : ProjectStoreFactoryImpl() {
  override fun createStore(project: Project): IProjectStore {
    LOG.assertTrue(!project.isDefault)
    return ProjectWithModuleStoreImpl(project)
  }
}

internal class PlatformProjectStoreFactory : ProjectStoreFactoryImpl() {
  override fun createStore(project: Project): IProjectStore {
    LOG.assertTrue(!project.isDefault)
    return ProjectStoreImpl(project)
  }
}

@CalledInAny
internal suspend fun ensureFilesWritable(project: Project, files: Collection<VirtualFile>): ReadonlyStatusHandler.OperationStatus {
  return withContext(Dispatchers.EDT) {
    ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files)
  }
}
