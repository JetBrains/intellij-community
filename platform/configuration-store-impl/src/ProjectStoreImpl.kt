// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.ide.highlighter.WorkspaceFileType
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.async.coroutineDispatchingContext
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCoreUtil
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.ex.ProjectNameProvider
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.project.impl.ProjectStoreClassProvider
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtilRt
import com.intellij.util.SmartList
import com.intellij.util.containers.computeIfAny
import com.intellij.util.containers.isNullOrEmpty
import com.intellij.util.io.*
import com.intellij.util.text.nullize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.CalledInAny
import java.nio.file.AccessDeniedException
import java.nio.file.Path
import java.nio.file.Paths

internal const val PROJECT_FILE = "\$PROJECT_FILE$"
internal const val PROJECT_CONFIG_DIR = "\$PROJECT_CONFIG_DIR$"

internal val IProjectStore.nameFile: Path
  get() = Paths.get(directoryStorePath, ProjectImpl.NAME_FILE)

internal val PROJECT_FILE_STORAGE_ANNOTATION = FileStorageAnnotation(PROJECT_FILE, false)
internal val DEPRECATED_PROJECT_FILE_STORAGE_ANNOTATION = FileStorageAnnotation(PROJECT_FILE, true)

// cannot be `internal`, used in Upsource
abstract class ProjectStoreBase(final override val project: Project) : ComponentStoreWithExtraComponents(), IProjectStore {
  // protected setter used in upsource
  // Zelix KlassMaster - ERROR: Could not find method 'getScheme()'
  var scheme: StorageScheme = StorageScheme.DEFAULT

  final override var loadPolicy: StateLoadPolicy = StateLoadPolicy.LOAD

  final override fun isOptimiseTestLoadSpeed(): Boolean = loadPolicy != StateLoadPolicy.LOAD

  final override fun getStorageScheme(): StorageScheme = scheme

  abstract override val storageManager: StateStorageManagerImpl

  protected val isDirectoryBased: Boolean
    get() = scheme == StorageScheme.DIRECTORY_BASED

  final override fun setOptimiseTestLoadSpeed(value: Boolean) {
    // we don't load default state in tests as app store does because
    // 1) we should not do it
    // 2) it was so before, so, we preserve old behavior (otherwise RunManager will load template run configurations)
    loadPolicy = if (value) StateLoadPolicy.NOT_LOAD else StateLoadPolicy.LOAD
  }

  override fun getProjectFilePath(): String = storageManager.expandMacro(PROJECT_FILE)

  /**
   * `null` for default or non-directory based project.
   */
  override fun getProjectConfigDir(): String? = if (isDirectoryBased) storageManager.expandMacro(PROJECT_CONFIG_DIR) else null

  final override fun getWorkspaceFilePath(): String = storageManager.expandMacro(StoragePathMacros.WORKSPACE_FILE)

  final override fun clearStorages() {
    storageManager.clearStorages()
  }

  final override fun loadProjectFromTemplate(defaultProject: Project) {
    runBlocking { defaultProject.stateStore.save() }

    val element = (defaultProject.stateStore as DefaultProjectStoreImpl).getStateCopy() ?: return
    LOG.runAndLogException {
      if (isDirectoryBased) {
        normalizeDefaultProjectElement(defaultProject, element, Paths.get(storageManager.expandMacro(PROJECT_CONFIG_DIR)))
      }
      else {
        moveComponentConfiguration(defaultProject, element) { if (it == "workspace.xml") Paths.get(workspaceFilePath) else Paths.get(projectFilePath) }
      }
    }
    (storageManager.getOrCreateStorage(PROJECT_FILE) as XmlElementStorage).setDefaultState(element)
  }

  final override fun getProjectBasePath(): String {
    if (isDirectoryBased) {
      val path = PathUtilRt.getParentPath(storageManager.expandMacro(PROJECT_CONFIG_DIR))
      if (Registry.`is`("store.basedir.parent.detection", true) && PathUtilRt.getFileName(path).startsWith("${Project.DIRECTORY_STORE_FOLDER}.")) {
        return PathUtilRt.getParentPath(PathUtilRt.getParentPath(path))
      }
      return path
    }
    else {
      return PathUtilRt.getParentPath(projectFilePath)
    }
  }

  // used in upsource
  protected suspend fun setPath(filePath: String, isRefreshVfs: Boolean) {
    val storageManager = storageManager
    val fs = LocalFileSystem.getInstance()
    if (filePath.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
      scheme = StorageScheme.DEFAULT

      storageManager.addMacro(PROJECT_FILE, filePath)

      val workspacePath = composeFileBasedProjectWorkSpacePath(filePath)
      storageManager.addMacro(StoragePathMacros.WORKSPACE_FILE, workspacePath)

      if (isRefreshVfs) {
        withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
          VfsUtil.markDirtyAndRefresh(false, true, false, fs.refreshAndFindFileByPath(filePath), fs.refreshAndFindFileByPath(workspacePath))
        }
      }

      if (ApplicationManager.getApplication().isUnitTestMode) {
        // load state only if there are existing files
        isOptimiseTestLoadSpeed = !Paths.get(filePath).toFile().exists()
      }
    }
    else {
      scheme = StorageScheme.DIRECTORY_BASED

      val configDir = "$filePath/${Project.DIRECTORY_STORE_FOLDER}"
      storageManager.addMacro(PROJECT_CONFIG_DIR, configDir)
      storageManager.addMacro(PROJECT_FILE, "$configDir/misc.xml")
      storageManager.addMacro(StoragePathMacros.WORKSPACE_FILE, "$configDir/workspace.xml")

      if (ApplicationManager.getApplication().isUnitTestMode) {
        // load state only if there are existing files
        isOptimiseTestLoadSpeed = !Paths.get(filePath).exists()
      }

      if (isRefreshVfs) {
        withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
          VfsUtil.markDirtyAndRefresh(false, true, true, fs.refreshAndFindFileByPath(configDir))
        }
      }
    }

    storageManager.addMacro(StoragePathMacros.CACHE_FILE, project.getProjectCachePath(cacheDirName = "workspace", extensionWithDot = ".xml").systemIndependentPath)
  }

  override fun <T> getStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation): List<Storage> {
    val storages = stateSpec.storages
    if (storages.isEmpty()) {
      return listOf(PROJECT_FILE_STORAGE_ANNOTATION)
    }

    if (isDirectoryBased) {
      var result: MutableList<Storage>? = null
      for (storage in storages) {
        if (storage.path != PROJECT_FILE) {
          if (result == null) {
            result = SmartList()
          }
          result.add(storage)
        }
      }

      if (result.isNullOrEmpty()) {
        return listOf(PROJECT_FILE_STORAGE_ANNOTATION)
      }
      else {
        result!!.sortWith(deprecatedComparator)
        if (isDirectoryBased) {
          StreamProviderFactory.EP_NAME.getExtensions(project).computeIfAny {
            LOG.runAndLogException { it.customizeStorageSpecs(component, storageManager, stateSpec, result!!, operation) }
          }?.let {
              // yes, DEPRECATED_PROJECT_FILE_STORAGE_ANNOTATION is not added in this case
              return it
            }
        }

        // if we create project from default, component state written not to own storage file, but to project file,
        // we don't have time to fix it properly, so, ancient hack restored
        if (result.first().path != StoragePathMacros.CACHE_FILE) {
          result.add(DEPRECATED_PROJECT_FILE_STORAGE_ANNOTATION)
        }
        return result
      }
    }
    else {
      var result: MutableList<Storage>? = null
      // FlexIdeProjectLevelCompilerOptionsHolder, FlexProjectLevelCompilerOptionsHolderImpl and CustomBeanRegistry
      var hasOnlyDeprecatedStorages = true
      for (storage in storages) {
        @Suppress("DEPRECATION")
        if (storage.path == PROJECT_FILE || storage.path == StoragePathMacros.WORKSPACE_FILE || storage.path == StoragePathMacros.CACHE_FILE) {
          if (result == null) {
            result = SmartList()
          }
          result.add(storage)
          if (!storage.deprecated) {
            hasOnlyDeprecatedStorages = false
          }
        }
      }
      if (result.isNullOrEmpty()) {
        return listOf(PROJECT_FILE_STORAGE_ANNOTATION)
      }
      else {
        if (hasOnlyDeprecatedStorages) {
          result!!.add(PROJECT_FILE_STORAGE_ANNOTATION)
        }
        result!!.sortWith(deprecatedComparator)
        return result
      }
    }
  }

  override fun isProjectFile(file: VirtualFile): Boolean {
    if (!file.isInLocalFileSystem || !ProjectCoreUtil.isProjectOrWorkspaceFile(file)) {
      return false
    }

    val filePath = file.path
    if (!isDirectoryBased) {
      return filePath == projectFilePath || filePath == workspaceFilePath
    }
    return FileUtil.isAncestor(PathUtilRt.getParentPath(projectFilePath), filePath, false)
  }

  override fun getDirectoryStorePath(ignoreProjectStorageScheme: Boolean): String? = if (!ignoreProjectStorageScheme && !isDirectoryBased) null else PathUtilRt.getParentPath(projectFilePath).nullize()

  override fun getDirectoryStoreFile(): VirtualFile? = directoryStorePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }

  override fun getDirectoryStorePathOrBase(): String = PathUtilRt.getParentPath(projectFilePath)
}

private open class ProjectStoreImpl(project: Project, private val pathMacroManager: PathMacroManager) : ProjectStoreBase(project) {
  private var lastSavedProjectName: String? = null

  init {
    assert(!project.isDefault)
  }

  final override fun getPathMacroManagerForDefaults() = pathMacroManager

  override val storageManager = ProjectStateStorageManager(TrackingPathMacroSubstitutorImpl(pathMacroManager), project)

  override fun setPath(path: String) {
    runBlocking {
      setPath(path, true)
    }
  }

  override fun getProjectName(): String {
    if (!isDirectoryBased) {
      return PathUtilRt.getFileName(projectFilePath).removeSuffix(ProjectFileType.DOT_DEFAULT_EXTENSION)
    }

    val baseDir = projectBasePath
    val nameFile = nameFile
    if (nameFile.exists()) {
      LOG.runAndLogException { readProjectNameFile(nameFile) }?.let {
        lastSavedProjectName = it
        return it
      }
    }

    return ProjectNameProvider.EP_NAME.extensionList.computeIfAny {
      LOG.runAndLogException { it.getDefaultName(project) }
    } ?: PathUtilRt.getFileName(baseDir).replace(":", "")
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
      if (currentProjectName == PathUtilRt.getFileName(basePath)) {
        // name equals to base path name - just remove name
        nameFile.delete()
      }
      else if (Paths.get(basePath).isDirectory()) {
        nameFile.write(currentProjectName.toByteArray())
      }
    }

    try {
      doSave()
    }
    catch (e: AccessDeniedException) {
      val status = ensureFilesWritable(project, listOf(LocalFileSystem.getInstance().refreshAndFindFileByPath(nameFile.systemIndependentPath)!!))
      if (status.hasReadonlyFiles()) {
        throw e
      }

      doSave()
    }
  }

  final override suspend fun doSave(result: SaveResult, isForceSavingAllSettings: Boolean) {
    coroutineScope {
      launch {
        // save modules before project
        val errors = SmartList<Throwable>()
        val moduleSaveSessions = saveModules(errors, isForceSavingAllSettings)
        result.addErrors(errors)

        (saveSettingsSavingComponentsAndCommitComponents(result, isForceSavingAllSettings) as ProjectSaveSessionProducerManager)
          .saveWithAdditionalSaveSessions(moduleSaveSessions)
          .appendTo(result)
      }

      val projectSaved = project.messageBus.syncPublisher(ProjectEx.ProjectSaved.TOPIC)
      launch {
        try {
          saveProjectName()
        }
        catch (e: Throwable) {
          LOG.error("Unable to store project name", e)
        }

        projectSaved.duringSave(project)
      }
      launch(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
        @Suppress("DEPRECATION")
        projectSaved.saved(project)
      }
    }
  }

  protected open suspend fun saveModules(errors: MutableList<Throwable>, isForceSavingAllSettings: Boolean): List<SaveSession> {
    return emptyList()
  }

  final override fun createSaveSessionProducerManager() = ProjectSaveSessionProducerManager(project)
}

private class ProjectWithModulesStoreImpl(project: Project, pathMacroManager: PathMacroManager) : ProjectStoreImpl(project, pathMacroManager) {
  override suspend fun saveModules(errors: MutableList<Throwable>, isForceSavingAllSettings: Boolean): List<SaveSession> {
    val modules = ModuleManager.getInstance(project)?.modules ?: Module.EMPTY_ARRAY
    if (modules.isEmpty()) {
      return emptyList()
    }

    return withContext(AppUIExecutor.onUiThread().inTransaction(project).coroutineDispatchingContext()) {
      // do no create with capacity because very rarely a lot of modules will be modified
      val saveSessions: MutableList<SaveSession> = SmartList<SaveSession>()
      // commit components
      for (module in modules) {
        val moduleStore = ModuleServiceManager.getService(module, IComponentStore::class.java) as ComponentStoreImpl
        // collectSaveSessions is very cheap, so, do it in EDT
        moduleStore.doCreateSaveSessionManagerAndSaveComponents(isForceSavingAllSettings, errors).collectSaveSessions(saveSessions)
      }
      saveSessions
    }
  }
}

// used in upsource
class PlatformLangProjectStoreClassProvider : ProjectStoreClassProvider {
  override fun getProjectStoreClass(isDefaultProject: Boolean): Class<out IComponentStore> {
    return if (isDefaultProject) DefaultProjectStoreImpl::class.java else ProjectWithModulesStoreImpl::class.java
  }
}

@Suppress("unused")
private class PlatformProjectStoreClassProvider : ProjectStoreClassProvider {
  override fun getProjectStoreClass(isDefaultProject: Boolean): Class<out IComponentStore> {
    return if (isDefaultProject) DefaultProjectStoreImpl::class.java else ProjectStoreImpl::class.java
  }
}

private fun composeFileBasedProjectWorkSpacePath(filePath: String) = "${FileUtilRt.getNameWithoutExtension(filePath)}${WorkspaceFileType.DOT_DEFAULT_EXTENSION}"

@CalledInAny
internal suspend fun ensureFilesWritable(project: Project, files: Collection<VirtualFile>): ReadonlyStatusHandler.OperationStatus {
  return withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
    ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files)
  }
}