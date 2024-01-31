// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.ide.highlighter.WorkspaceFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCoreUtil
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.ex.ProjectNameProvider
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.project.projectsDataDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.util.io.Ksuid
import com.intellij.util.io.delete
import com.intellij.util.io.write
import com.intellij.util.messages.MessageBus
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory

internal const val PROJECT_FILE = "\$PROJECT_FILE\$"
internal const val PROJECT_CONFIG_DIR = "\$PROJECT_CONFIG_DIR\$"

internal val PROJECT_FILE_STORAGE_ANNOTATION = FileStorageAnnotation(PROJECT_FILE, false)
private val DEPRECATED_PROJECT_FILE_STORAGE_ANNOTATION = FileStorageAnnotation(PROJECT_FILE, true)

@ApiStatus.Internal
open class ProjectStoreImpl(final override val project: Project) : ComponentStoreWithExtraComponents(), IProjectStore {
  private var dirOrFile: Path? = null
  private var dotIdea: Path? = null

  private var lastSavedProjectName: String? = null

  init {
    assert(!project.isDefault)
  }

  override val serviceContainer: ComponentManagerImpl
    get() = project as ComponentManagerImpl

  internal fun getNameFile(): Path {
    for (projectNameProvider in ProjectNameProvider.EP_NAME.lazySequence()) {
      runCatching { projectNameProvider.getNameFile(project) }
        .getOrLogException(LOG)
        ?.let { return it }
    }
    return directoryStorePath!!.resolve(ProjectEx.NAME_FILE)
  }

  final override var loadPolicy: StateLoadPolicy = StateLoadPolicy.LOAD

  final override fun isOptimiseTestLoadSpeed(): Boolean = loadPolicy != StateLoadPolicy.LOAD

  final override fun getStorageScheme(): StorageScheme = if (dotIdea == null) StorageScheme.DEFAULT else StorageScheme.DIRECTORY_BASED

  override val storageManager: StateStorageManagerImpl =
    ProjectStateStorageManager(TrackingPathMacroSubstitutorImpl(PathMacroManager.getInstance(project)), project)

  protected val isDirectoryBased: Boolean
    get() = dotIdea != null

  final override fun setOptimiseTestLoadSpeed(value: Boolean) {
    loadPolicy = if (value) StateLoadPolicy.NOT_LOAD else StateLoadPolicy.LOAD
  }

  final override fun getProjectFilePath(): Path = storageManager.expandMacro(PROJECT_FILE)

  final override fun getWorkspacePath(): Path = storageManager.expandMacro(StoragePathMacros.WORKSPACE_FILE)

  final override fun clearStorages(): Unit = storageManager.clearStorages()

  final override fun getPathMacroManagerForDefaults() = PathMacroManager.getInstance(project)

  override fun setPath(path: Path) {
    setPath(file = path, isRefreshVfsNeeded = true, template = null)
  }

  override fun setPath(file: Path, isRefreshVfsNeeded: Boolean, template: Project?) {
    dirOrFile = file

    val storageManager = storageManager
    val isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode
    val macros = ArrayList<Macro>(5)
    if (file.toString().endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
      macros.add(Macro(PROJECT_FILE, file))

      val workspacePath = file.parent.resolve("${file.fileName.toString().removeSuffix(ProjectFileType.DOT_DEFAULT_EXTENSION)}${WorkspaceFileType.DOT_DEFAULT_EXTENSION}")
      macros.add(Macro(StoragePathMacros.WORKSPACE_FILE, workspacePath))

      if (isUnitTestMode) {
        // we don't load default state in tests as app store does, because:
        // 1) we should not do it
        // 2) it was so before, so, we preserve the old behavior (otherwise RunManager will load template run configurations)
        // load state only if there are existing files
        @Suppress("TestOnlyProblems") val componentStoreLoadingEnabled = project.getUserData(IProjectStore.COMPONENT_STORE_LOADING_ENABLED)
        if (if (componentStoreLoadingEnabled == null) !Files.exists(file) else !componentStoreLoadingEnabled) {
          loadPolicy = StateLoadPolicy.NOT_LOAD
        }
        macros.add(Macro(StoragePathMacros.PRODUCT_WORKSPACE_FILE, workspacePath))
      }
    }
    else {
      val dotIdea = file.resolve(Project.DIRECTORY_STORE_FOLDER)
      this.dotIdea = dotIdea

      // PROJECT_CONFIG_DIR must be the first macro
      macros.add(Macro(PROJECT_CONFIG_DIR, dotIdea))
      macros.add(Macro(StoragePathMacros.WORKSPACE_FILE, dotIdea.resolve("workspace.xml")))
      macros.add(Macro(PROJECT_FILE, dotIdea.resolve("misc.xml")))

      if (isUnitTestMode) {
        // load state only if there are existing files
        isOptimiseTestLoadSpeed = !Files.exists(file)

        macros.add(Macro(StoragePathMacros.PRODUCT_WORKSPACE_FILE, dotIdea.resolve("product-workspace.xml")))
      }
    }

    val presentableUrl = if (dotIdea == null) file else projectBasePath

    val cacheFileName = getProjectCacheFileName(presentableUrl = presentableUrl.invariantSeparatorsPathString, projectName = "")
    macros.add(Macro(StoragePathMacros.CACHE_FILE, projectsDataDir.resolve(cacheFileName).resolve("cache-state.xml")))

    storageManager.setMacros(macros)

    if (template != null) {
      loadProjectFromTemplate(template)
    }

    val projectIdManager = ProjectIdManager.getInstance(project)
    var projectWorkspaceId = projectIdManager.id
    if (projectWorkspaceId == null) {
      // do not use the project name as part of id, to ensure a project dir rename does not cause data loss
      projectWorkspaceId = Ksuid.generate()
      projectIdManager.id = projectWorkspaceId
    }

    if (isUnitTestMode) {
      return
    }

    val productWorkspaceFile = PathManager.getConfigDir()
      .resolve("workspace")
      .resolve("$projectWorkspaceId.xml")
    macros.add(Macro(StoragePathMacros.PRODUCT_WORKSPACE_FILE, productWorkspaceFile))
    storageManager.setMacros(macros)
  }

  private fun loadProjectFromTemplate(defaultProject: Project) {
    val element = (defaultProject.stateStore as DefaultProjectStoreImpl).getStateCopy() ?: return
    runCatching {
      val dotIdea = dotIdea
      if (dotIdea != null) {
        normalizeDefaultProjectElement(defaultProject, element, dotIdea)
      }
      else {
        moveComponentConfiguration(
          defaultProject, element,
          storagePathResolver = { PROJECT_FILE },  // doesn't matter; any path will be resolved as projectFilePath (see `fileResolver`)
          fileResolver = { if (it == "workspace.xml") workspacePath else dirOrFile!! })
      }
    }.getOrLogException(LOG)
  }

  final override fun getProjectBasePath(): Path {
    val path = dirOrFile ?: throw IllegalStateException("setPath was not yet called")
    if (isDirectoryBased) {
      val useParent = System.getProperty("store.basedir.parent.detection", "true").toBoolean() &&
                      (path.fileName?.toString()?.startsWith("${Project.DIRECTORY_STORE_FOLDER}.") == true)
      return if (useParent) path.parent.parent else path
    }
    else {
      return path.parent
    }
  }

  final override fun getLocationHash(): String {
    val prefix: String
    val path: Path
    if (storageScheme == StorageScheme.DIRECTORY_BASED) {
      path = dirOrFile ?: throw IllegalStateException("setPath was not yet called")
      prefix = ""
    }
    else {
      path = projectFilePath
      prefix = projectName
    }
    return "${prefix}${Integer.toHexString(path.invariantSeparatorsPathString.hashCode())}"
  }

  override fun getPresentableUrl(): String =
    if (isDirectoryBased) {
      (dirOrFile ?: throw IllegalStateException("setPath was not yet called")).invariantSeparatorsPathString
    }
    else {
      projectFilePath.invariantSeparatorsPathString
    }

  override fun getProjectWorkspaceId(): String? = ProjectIdManager.getInstance(project).id

  override fun <T> getStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation): List<Storage> {
    val storages = stateSpec.storages
    if (isDirectoryBased) {
      if (storages.size == 2 && ApplicationManager.getApplication().isUnitTestMode &&
          isSpecialStorage(storages.first().path) &&
          storages[1].path == StoragePathMacros.WORKSPACE_FILE) {
        return listOf(storages.first())
      }

      val result = mutableListOf<Storage>()
      for (storage in storages) {
        if (storage.path != PROJECT_FILE) {
          result.add(storage)
        }
      }
      if (result.isEmpty()) {
        result.add(PROJECT_FILE_STORAGE_ANNOTATION)
      }
      else {
        result.sortWith(deprecatedComparator)
      }
      for (providerFactory in StreamProviderFactory.EP_NAME.asSequence(project)) {
        val customizedSpecs = runCatching {
          // yes, DEPRECATED_PROJECT_FILE_STORAGE_ANNOTATION is not added in this case
          providerFactory.customizeStorageSpecs(component, storageManager, stateSpec, result, operation)
        }.getOrLogException(LOG)
        if (customizedSpecs != null) {
          return customizedSpecs
        }
      }
      @Suppress("GrazieInspection")
      if (!isSpecialStorage(result.first().path)) {
        // if we create project from default, component state written not to own storage file, but to project file,
        // we don't have time to fix it properly, so, ancient hack restored
        result.add(DEPRECATED_PROJECT_FILE_STORAGE_ANNOTATION)
      }
      return result
    }
    else {
      val result = mutableListOf<Storage>()
      var hasOnlyDeprecatedStorages = true
      for (storage in storages) {
        if (storage.path == PROJECT_FILE || storage.path == StoragePathMacros.WORKSPACE_FILE || isSpecialStorage(storage.path)) {
          result.add(storage)
          if (!storage.deprecated) {
            hasOnlyDeprecatedStorages = false
          }
        }
      }
      if (result.isEmpty()) {
        return listOf(PROJECT_FILE_STORAGE_ANNOTATION)
      }
      else {
        if (hasOnlyDeprecatedStorages) {
          result.add(PROJECT_FILE_STORAGE_ANNOTATION)
        }
        result.sortWith(deprecatedComparator)
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
      return filePath == projectFilePath.invariantSeparatorsPathString || filePath == workspacePath.invariantSeparatorsPathString
    }
    return VfsUtilCore.isAncestorOrSelf(projectFilePath.parent.invariantSeparatorsPathString, file)
  }

  final override fun getDirectoryStorePath(): Path? = dotIdea

  final override fun reloadStates(componentNames: Set<String>, messageBus: MessageBus) {
    runBatchUpdate(project) {
      reinitComponents(componentNames)
    }
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
    try {
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
    catch (t: Throwable) {
      LOG.error("Unable to store project name", t)
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
        saveProjectName()
      }
    }
  }

  internal open suspend fun saveModules(
    saveSessions: MutableList<SaveSession>,
    saveResult: SaveResult,
    forceSavingAllSettings: Boolean,
    projectSessionManager: ProjectSaveSessionProducerManager
  ) { }

  override fun createSaveSessionProducerManager(): ProjectSaveSessionProducerManager =
    ProjectSaveSessionProducerManager(project, storageManager.isUseVfsForWrite())

  final override fun commitObsoleteComponents(sessionManager: SaveSessionProducerManager, isProjectLevel: Boolean) {
    if (isDirectoryBased) {
      super.commitObsoleteComponents(sessionManager, true)
    }
  }

  private class ProjectStateStorageManager(macroSubstitutor: PathMacroSubstitutor, private val project: Project)
    : StateStorageManagerImpl("project", macroSubstitutor, componentManager = project)
  {
    override fun isUseVfsForWrite(): Boolean = true

    override fun normalizeFileSpec(fileSpec: String): String =
      removeMacroIfStartsWith(super.normalizeFileSpec(fileSpec), PROJECT_CONFIG_DIR)

    override fun expandMacro(collapsedPath: String): Path =
      if (collapsedPath[0] == '$') super.expandMacro(collapsedPath)
      else macros[0].value.resolve(collapsedPath)  // PROJECT_CONFIG_DIR is the first macro

    override fun beforeElementSaved(elements: MutableList<Element>, rootAttributes: MutableMap<String, String>) {
      rootAttributes.put(VERSION_OPTION, "4")
    }

    override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String =
      if (ComponentManagerImpl.badWorkspaceComponents.contains(componentName)) StoragePathMacros.WORKSPACE_FILE
      else PROJECT_FILE

    override val isExternalSystemStorageEnabled: Boolean
      get() = project.isExternalStorageEnabled
  }
}
