// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.configurationStore

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.ide.highlighter.WorkspaceFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.project.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.settings.SettingsController
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.util.io.Ksuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.invariantSeparatorsPathString

internal const val VERSION_OPTION: String = "version"

internal const val PROJECT_FILE: String = $$"$PROJECT_FILE$"
internal const val PROJECT_CONFIG_DIR: String = $$"$PROJECT_CONFIG_DIR$"

internal val PROJECT_FILE_STORAGE_ANNOTATION: FileStorageAnnotation = FileStorageAnnotation(PROJECT_FILE, false)

@ApiStatus.Internal
open class ProjectStoreImpl(final override val project: Project) : ComponentStoreWithExtraComponents(), IProjectStore {
  override val isExternalStorageSupported: Boolean
    get() = storeDescriptor.isExternalStorageSupported

  lateinit var storeDescriptor: ProjectStoreDescriptor

  init {
    assert(!project.isDefault)
  }

  override val serviceContainer: ComponentManagerEx
    get() = project as ComponentManagerEx

  final override var loadPolicy: StateLoadPolicy = StateLoadPolicy.LOAD

  override var isOptimiseTestLoadSpeed: Boolean
    get() = loadPolicy != StateLoadPolicy.LOAD
    set(value) {
      loadPolicy = if (value) StateLoadPolicy.NOT_LOAD else StateLoadPolicy.LOAD
    }

  override val storageScheme: StorageScheme
    get() = if (storeDescriptor.isDirectoryBased) StorageScheme.DIRECTORY_BASED else StorageScheme.DEFAULT

  final override val storageManager: StateStorageManagerImpl = ProjectStateStorageManager(
    project = project,
    isExternalStorageSupported = { storeDescriptor.isExternalStorageSupported },
  )

  @Volatile
  final override var isStoreInitialized: Boolean = false
    private set

  override val projectFilePath: Path
    get() = storageManager.expandMacro(PROJECT_FILE)

  override val workspacePath: Path
    get() = storageManager.expandMacro(StoragePathMacros.WORKSPACE_FILE)

  final override fun clearStorages() {
    storageManager.clearStorages()
  }

  final override fun getPathMacroManagerForDefaults(): PathMacroManager = PathMacroManager.getInstance(project)

  final override fun setPath(path: Path) {
    setPath(file = path, template = null)
  }

  final override fun setPath(file: Path, template: Project?) {
    LOG.info("Project store initialization started for path: $file and template: $template")

    val storageManager = storageManager
    val isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode
    val macros = ArrayList<Macro>(5)
    val iprFile: Path?
    if (file.toString().endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
      iprFile = file

      macros.add(Macro(PROJECT_FILE, file))

      val userBaseDir = file.parent
      val workspacePath = userBaseDir.resolve("${file.fileName.toString().removeSuffix(ProjectFileType.DOT_DEFAULT_EXTENSION)}${WorkspaceFileType.DOT_DEFAULT_EXTENSION}")
      macros.add(Macro(StoragePathMacros.WORKSPACE_FILE, workspacePath))

      if (isUnitTestMode) {
        // we don't load the default state in tests as the app store does, because:
        // 1) we should not do it
        // 2) it was so before, so, we preserve the old behavior (otherwise RunManager will load template run configurations)
        // load state only if there are existing files
        @Suppress("TestOnlyProblems") val componentStoreLoadingEnabled = project.getUserData(IProjectStore.COMPONENT_STORE_LOADING_ENABLED)
        if (if (componentStoreLoadingEnabled == null) Files.notExists(file) else !componentStoreLoadingEnabled) {
          loadPolicy = StateLoadPolicy.NOT_LOAD
        }
        macros.add(Macro(StoragePathMacros.PRODUCT_WORKSPACE_FILE, workspacePath))
      }

      storeDescriptor = object : ProjectStoreDescriptor {
        override val projectIdentityDir: Path
          get() = userBaseDir
        override val historicalProjectBasePath: Path
          get() = userBaseDir
        override val isDirectoryBased: Boolean
          get() = false

        override val dotIdea: Path?
          get() = null

        override fun getProjectName(): String {
          return file.fileName.toString().removeSuffix(ProjectFileType.DOT_DEFAULT_EXTENSION)
        }

        override suspend fun saveProjectName(project: Project) {
        }

        override fun getJpsBridgeAwareStorageSpec(filePath: String, project: Project): Storage {
          return doGetJpsBridgeAwareStorageSpec(filePath, project)
        }
      }
    }
    else {
      iprFile = null

      val storeDescriptor = ProjectStorePathManager.getInstance().getStoreDescriptor(file)
      this.storeDescriptor = storeDescriptor

      // PROJECT_CONFIG_DIR must be the first macro
      val dotIdea = storeDescriptor.dotIdea!!
      macros.add(Macro(PROJECT_CONFIG_DIR, dotIdea))
      macros.add(Macro(StoragePathMacros.WORKSPACE_FILE, dotIdea.resolve("workspace.xml")))
      macros.add(Macro(PROJECT_FILE, dotIdea.resolve("misc.xml")))

      if (isUnitTestMode) {
        // load state only if there are existing files
        isOptimiseTestLoadSpeed = Files.notExists(file)

        macros.add(Macro(StoragePathMacros.PRODUCT_WORKSPACE_FILE, dotIdea.resolve("product-workspace.xml")))
      }

      val customMacros = storeDescriptor.customMacros()
      if (customMacros.isNotEmpty()) {
        macros.removeIf { it.key in customMacros }
        for ((key, value) in customMacros) {
          macros.add(Macro(key, value))
        }

        (storageManager as ProjectStateStorageManager).setCustomMacros(customMacros)
      }
    }

    val presentableUrl = if (storeDescriptor.dotIdea == null) file else storeDescriptor.projectIdentityDir

    val cacheFileName = getProjectCacheFileName(presentableUrl = presentableUrl.invariantSeparatorsPathString, projectName = "")
    macros.add(Macro(StoragePathMacros.CACHE_FILE, projectsDataDir.resolve(cacheFileName).resolve("cache-state.xml")))

    storageManager.setMacros(macros)

    if (template != null) {
      loadProjectFromTemplate(template, iprFile)
    }

    val projectIdManager = ProjectIdManager.getInstance(project)
    var projectWorkspaceId = projectIdManager.id
    if (projectWorkspaceId == null) {
      // do not use the project name as part of id, to ensure a project dir rename does not cause data loss
      projectWorkspaceId = Ksuid.generate()
      projectIdManager.id = projectWorkspaceId
    }

    if (!isUnitTestMode) {
      // IJPL-166131
      val basePath = if (Registry.`is`("rdct.persist.project.settings", false)) {
        PathManager.getOriginalConfigDir()
      }
      else {
        PathManager.getConfigDir()
      }
      val productWorkspaceFile = basePath.resolve("workspace/$projectWorkspaceId.xml")
      macros.add(Macro(StoragePathMacros.PRODUCT_WORKSPACE_FILE, productWorkspaceFile))
      /// todo why we call it the second time?
      storageManager.setMacros(macros)
    }
    isStoreInitialized = true
    LOG.info("Project store initialized with paths: $macros")
  }

  private fun loadProjectFromTemplate(defaultProject: Project, iprFile: Path?) {
    val element = (defaultProject.stateStore as DefaultProjectStoreImpl).getStateCopy() ?: return
    runCatching {
      if (iprFile == null) {
        normalizeDefaultProjectElement(defaultProject, element, projectConfigDir = storeDescriptor.dotIdea!!)
      }
      else {
        moveComponentConfiguration(
          defaultProject, element,
          storagePathResolver = { PROJECT_FILE },  // doesn't matter; any path will be resolved as projectFilePath (see `fileResolver`)
          fileResolver = { if (it == "workspace.xml") workspacePath else iprFile },
        )
      }
    }.getOrLogException(LOG)
  }

  final override val projectBasePath: Path
    get() = storeDescriptor.historicalProjectBasePath

  final override val locationHash: String
    get() {
      val prefix: String
      val path: Path
      if (storeDescriptor.isDirectoryBased) {
        path = storeDescriptor.projectIdentityDir
        prefix = ""
      }
      else {
        path = projectFilePath
        prefix = projectName
      }
      return "$prefix${Integer.toHexString(path.invariantSeparatorsPathString.hashCode())}"
    }

  override val presentableUrl: String
    get() {
      if (storeDescriptor.isDirectoryBased) {
        return storeDescriptor.projectIdentityDir.invariantSeparatorsPathString
      }
      else {
        return projectFilePath.invariantSeparatorsPathString
      }
    }

  override val projectWorkspaceId: String?
    get() = ProjectIdManager.getInstance(project).id

  final override fun <T> getStorageSpecs(
    component: PersistentStateComponent<T>,
    stateSpec: State,
    operation: StateStorageOperation,
  ): List<Storage> {
    val storages = stateSpec.storages
    if (storeDescriptor.isDirectoryBased) {
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

      return storeDescriptor.customizeStorageSpecs(component = component, storageManager = storageManager, stateSpec = stateSpec, storages = result, operation = operation)
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

  final override fun isProjectFile(file: VirtualFile): Boolean {
    if (!file.isInLocalFileSystem || !ProjectCoreUtil.isProjectOrWorkspaceFile(file)) {
      return false
    }
    val filePath = file.path
    if (!storeDescriptor.isDirectoryBased) {
      return filePath == projectFilePath.invariantSeparatorsPathString || filePath == workspacePath.invariantSeparatorsPathString
    }
    return VfsUtilCore.isAncestorOrSelf(projectFilePath.parent.invariantSeparatorsPathString, file)
  }

  final override val directoryStorePath: Path?
    get() = storeDescriptor.dotIdea

  final override fun reloadStates(componentNames: Set<String>) {
    batchReloadStates(componentNames, project.messageBus)
  }

  final override val projectName: String
    get() = storeDescriptor.getProjectName()

  final override suspend fun doSave(saveResult: SaveResult, forceSavingAllSettings: Boolean) {
    coroutineScope {
      launch {
        // save modules before the project
        val saveSessions = Collections.synchronizedList(ArrayList<SaveSession>())
        val projectSessionManager = createSaveSessionProducerManager()
        saveModules(saveSessions, saveResult, forceSavingAllSettings, projectSessionManager)
        saveSettingsAndCommitComponents(saveResult, forceSavingAllSettings, projectSessionManager)
        projectSessionManager.collectSaveSessions(saveSessions)
        if (saveSessions.isNotEmpty()) {
          projectSessionManager.saveAndValidate(saveSessions, saveResult)
        }
      }

      launch {
        storeDescriptor.saveProjectName(project)
      }
    }
  }

  internal open suspend fun saveModules(
    saveSessions: MutableList<SaveSession>,
    saveResult: SaveResult,
    forceSavingAllSettings: Boolean,
    projectSessionManager: ProjectSaveSessionProducerManager,
  ) {
  }

  override fun createSaveSessionProducerManager(): ProjectSaveSessionProducerManager = ProjectSaveSessionProducerManager(project, storageManager.isUseVfsForWrite)

  final override fun commitObsoleteComponents(session: SaveSessionProducerManager, isProjectLevel: Boolean) {
    if (storeDescriptor.isDirectoryBased) {
      super.commitObsoleteComponents(session = session, isProjectLevel = true)
    }
  }
}

private class ProjectStateStorageManager(private val project: Project, private val isExternalStorageSupported: () -> Boolean) : StateStorageManagerImpl(
  rootTagName = "project",
  macroSubstitutor = TrackingPathMacroSubstitutorImpl(PathMacroManager.getInstance(project)),
  componentManager = project,
  controller = ApplicationManager.getApplication().getService(SettingsController::class.java)?.createChild(project),
) {
  private var customMacros: Map<String, Path> = emptyMap()

  override val isUseVfsForWrite: Boolean
    get() = !useBackgroundSave()

  override fun normalizeFileSpec(fileSpec: String): String = removeMacroIfStartsWith(path = super.normalizeFileSpec(fileSpec), macro = PROJECT_CONFIG_DIR)

  fun setCustomMacros(customMacros: Map<String, Path>) {
    this.customMacros = customMacros
  }

  override fun expandMacro(collapsedPath: String): Path {
    if (collapsedPath[0] == '$') {
      return super.expandMacro(collapsedPath)
    }

    customMacros.get(collapsedPath)?.let {
      return it
    }
    // PROJECT_CONFIG_DIR is the first macro
    return macros[0].value.resolve(collapsedPath)
  }

  override fun beforeElementSaved(elements: MutableList<Element>, rootAttributes: MutableMap<String, String>) {
    rootAttributes.put(VERSION_OPTION, "4")
  }

  override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String {
    return if (ComponentManagerImpl.badWorkspaceComponents.contains(componentName)) StoragePathMacros.WORKSPACE_FILE else PROJECT_FILE
  }

  override val isExternalSystemStorageEnabled: Boolean
    get() = isExternalStorageSupported() && project.isExternalStorageEnabled
}

@CalledInAny
internal suspend fun ensureFilesWritable(project: Project, files: Collection<VirtualFile>): ReadonlyStatusHandler.OperationStatus {
  return withContext(Dispatchers.EDT) {
    ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(files)
  }
}

internal fun useBackgroundSave(): Boolean = Registry.`is`("ide.background.save.settings", true)
