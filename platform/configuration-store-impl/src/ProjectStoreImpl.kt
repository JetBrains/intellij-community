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
import com.intellij.openapi.util.io.FileUtil.sanitizeFileName
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.eel.provider.asEelPath
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
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.invariantSeparatorsPathString

internal const val VERSION_OPTION: String = "version"

internal const val PROJECT_CONFIG_DIR: String = $$"$PROJECT_CONFIG_DIR$"

private const val CONFIG_WORKSPACE_DIR = "workspace"

@ApiStatus.Internal
open class ProjectStoreImpl(final override val project: Project) : ComponentStoreWithExtraComponents(), IProjectStore {
  override val isExternalStorageSupported: Boolean
    get() = storeDescriptor.isExternalStorageSupported

  override lateinit var storeDescriptor: ProjectStoreDescriptor

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
    get() = if (storeDescriptor.dotIdea == null) StorageScheme.DEFAULT else StorageScheme.DIRECTORY_BASED

  final override val storageManager: StateStorageManagerImpl = ProjectStateStorageManager(
    project = project,
    isExternalStorageSupported = { storeDescriptor.isExternalStorageSupported },
  )

  @Volatile
  final override var isStoreInitialized: Boolean = false
    private set

  override val projectFilePath: Path
    get() = storageManager.expandMacro(StoragePathMacros.PROJECT_FILE)

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
    val storeDescriptor = ProjectStorePathManager.getInstance().getStoreDescriptor(file)
    this.storeDescriptor = storeDescriptor
    val machineWorkspacePath = getMachineWorkspacePath(storeDescriptor)

    if (storeDescriptor is IprProjectStoreDescriptor) {
      iprFile = file

      macros.add(Macro(StoragePathMacros.PROJECT_FILE, file))

      val userBaseDir = file.parent
      val workspacePath = userBaseDir.resolve("${file.fileName.toString().removeSuffix(ProjectFileType.DOT_DEFAULT_EXTENSION)}${WorkspaceFileType.DOT_DEFAULT_EXTENSION}")
      macros.add(Macro(StoragePathMacros.WORKSPACE_FILE, machineWorkspacePath ?: workspacePath))

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
    }
    else {
      iprFile = null

      // PROJECT_CONFIG_DIR must be the first macro
      val dotIdea = storeDescriptor.dotIdea!!
      macros.add(Macro(PROJECT_CONFIG_DIR, dotIdea))
      macros.add(Macro(StoragePathMacros.WORKSPACE_FILE, machineWorkspacePath ?: dotIdea.resolve("workspace.xml")))
      macros.add(Macro(StoragePathMacros.PROJECT_FILE, dotIdea.resolve("misc.xml")))

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

    val presentableUrl = if (storeDescriptor.dotIdea == null) file else storeDescriptor.projectIdentityFile

    val cacheFileName = getProjectCacheFileName(presentableUrl = presentableUrl.invariantSeparatorsPathString, projectName = "")
    macros.add(Macro(StoragePathMacros.CACHE_FILE, projectsDataDir.resolve(cacheFileName).resolve("cache-state.xml")))

    storageManager.setMacros(macros)

    if (template != null) {
      loadProjectFromTemplate(template, iprFile)
    }

    val projectIdManager = project.service<ProjectIdManager>()
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
      val productWorkspaceFile = basePath.resolve("$CONFIG_WORKSPACE_DIR/$projectWorkspaceId.xml")
      // storageManager.setMacros(macros) was called before, because we need to read a `ProjectIdManager` state to get projectWorkspaceId
      macros.add(Macro(StoragePathMacros.PRODUCT_WORKSPACE_FILE, productWorkspaceFile))
    }
    isStoreInitialized = true
    LOG.info("Project store initialized with paths: $macros")
  }

  private fun loadProjectFromTemplate(defaultProject: Project, iprFile: Path?) {
    val element = (defaultProject.stateStore as DefaultProjectStoreImpl).getStateCopy() ?: return
    runCatching {
      if (iprFile == null) {
        normalizeDefaultProjectElement(defaultProject = defaultProject, element = element, projectConfigDir = storeDescriptor.dotIdea!!)
      }
      else {
        moveComponentConfiguration(
          defaultProject = defaultProject,
          element = element,
          storagePathResolver = { StoragePathMacros.PROJECT_FILE },  // doesn't matter; any path will be resolved as projectFilePath (see `fileResolver`)
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
      if (storeDescriptor.dotIdea == null) {
        path = projectFilePath
        prefix = storeDescriptor.projectName
      }
      else {
        path = storeDescriptor.projectIdentityFile
        prefix = ""
      }
      return "$prefix${Integer.toHexString(path.invariantSeparatorsPathString.hashCode())}"
    }

  override val projectWorkspaceId: String?
    get() = project.service<ProjectIdManager>().id

  final override fun <T : Any> getStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation): List<Storage> {
    return storeDescriptor.getStorageSpecs(component = component, stateSpec = stateSpec, operation = operation, storageManager = storageManager)
  }

  final override fun isProjectFile(file: VirtualFile): Boolean {
    if (!file.isInLocalFileSystem || !ProjectCoreUtil.isProjectOrWorkspaceFile(file, file.nameSequence)) {
      return false
    }

    val filePath = file.path
    val dotIdea = storeDescriptor.dotIdea
    if (dotIdea == null) {
      return filePath == storeDescriptor.presentableUrl.invariantSeparatorsPathString ||
             filePath == storageManager.expandMacro(StoragePathMacros.WORKSPACE_FILE).invariantSeparatorsPathString
    }
    else {
      return VfsUtilCore.isAncestorOrSelf(dotIdea.invariantSeparatorsPathString, file)
    }
  }

  final override val directoryStorePath: Path?
    get() = storeDescriptor.dotIdea

  final override suspend fun reloadStates(componentNames: Set<String>) {
    batchReloadStates(componentNames, project.messageBus)
  }

  final override suspend fun doSave(saveResult: SaveResult, forceSavingAllSettings: Boolean) {
    coroutineScope {
      launch {
        // save modules before the project
        val saveSessions = Collections.synchronizedList(ArrayList<SaveSession>())
        val projectSessionManager = createSaveSessionProducerManager()
        saveModules(saveSessions = saveSessions, saveResult = saveResult, forceSavingAllSettings = forceSavingAllSettings, projectSessionManager = projectSessionManager)
        saveSettingsAndCommitComponents(saveResult = saveResult, forceSavingAllSettings = forceSavingAllSettings, sessionManager = projectSessionManager)
        projectSessionManager.collectSaveSessions(saveSessions)
        if (saveSessions.isNotEmpty()) {
          saveSessions(saveSessions = saveSessions, saveResult = saveResult, collectVfsEvents = true)
          validateSaveResult(saveResult, project)
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
    projectSessionManager: SaveSessionProducerManager,
  ) {
  }

  final override val collectVfsEventsDuringSave: Boolean
    get() = true

  final override fun commitObsoleteComponents(session: SaveSessionProducerManager, isProjectLevel: Boolean) {
    if (storeDescriptor.dotIdea != null) {
      super.commitObsoleteComponents(session = session, isProjectLevel = true)
    }
  }

  private fun getMachineWorkspacePath(storeDescriptor: ProjectStoreDescriptor): Path? {
    val projectPath = storeDescriptor.historicalProjectBasePath
    if (projectPath.fileSystem != FileSystems.getDefault()) return null
    val descriptor = projectPath.asEelPath().descriptor
    if (descriptor::class.simpleName != "DockerEelDescriptor") return null
    val pathHash = FileUtilRt.pathHashCode(projectBasePath.invariantSeparatorsPathString)
    return PathManager.getOriginalConfigDir().resolve("$CONFIG_WORKSPACE_DIR/${sanitizeFileName(descriptor.machine.name)}.${pathHash.toHexString()}.xml")
  }
}

private class ProjectStateStorageManager(private val project: Project, private val isExternalStorageSupported: () -> Boolean) : StateStorageManagerImpl(
  rootTagName = "project",
  macroSubstitutor = TrackingPathMacroSubstitutorImpl(project.service<PathMacroManager>()),
  componentManager = project,
  controller = ApplicationManager.getApplication().getService(SettingsController::class.java)?.createChild(project),
) {
  private var customMacros: Map<String, Path> = emptyMap()

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
    return if (ComponentManagerImpl.badWorkspaceComponents.contains(componentName)) StoragePathMacros.WORKSPACE_FILE else StoragePathMacros.PROJECT_FILE
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
