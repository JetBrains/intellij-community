// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.ide.highlighter.WorkspaceFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCoreUtil
import com.intellij.openapi.project.doGetProjectFileName
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.ex.ProjectNameProvider
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SmartList
import com.intellij.util.io.Ksuid
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.messages.MessageBus
import com.intellij.util.text.nullize
import org.jetbrains.annotations.NonNls
import java.nio.file.Files
import java.nio.file.Path

@NonNls internal const val PROJECT_FILE = "\$PROJECT_FILE$"
@NonNls internal const val PROJECT_CONFIG_DIR = "\$PROJECT_CONFIG_DIR$"

internal val PROJECT_FILE_STORAGE_ANNOTATION = FileStorageAnnotation(PROJECT_FILE, false)
private val DEPRECATED_PROJECT_FILE_STORAGE_ANNOTATION = FileStorageAnnotation(PROJECT_FILE, true)

// cannot be `internal`, used in Upsource
abstract class ProjectStoreBase(final override val project: Project) : ComponentStoreWithExtraComponents(), IProjectStore {
  private var dirOrFile: Path? = null
  private var dotIdea: Path? = null

  internal fun getNameFile(): Path {
    for (projectNameProvider in ProjectNameProvider.EP_NAME.lazySequence()) {
      LOG.runAndLogException { projectNameProvider.getNameFile(project)?.let { return it } }
    }
    return directoryStorePath!!.resolve(ProjectEx.NAME_FILE)
  }

  final override var loadPolicy = StateLoadPolicy.LOAD

  final override fun isOptimiseTestLoadSpeed() = loadPolicy != StateLoadPolicy.LOAD

  final override fun getStorageScheme() = if (dotIdea == null) StorageScheme.DEFAULT else StorageScheme.DIRECTORY_BASED

  abstract override val storageManager: StateStorageManagerImpl

  protected val isDirectoryBased: Boolean
    get() = dotIdea != null

  final override fun setOptimiseTestLoadSpeed(value: Boolean) {
    loadPolicy = if (value) StateLoadPolicy.NOT_LOAD else StateLoadPolicy.LOAD
  }

  final override fun getProjectFilePath() = storageManager.expandMacro(PROJECT_FILE)

  final override fun getWorkspacePath() = storageManager.expandMacro(StoragePathMacros.WORKSPACE_FILE)

  final override fun clearStorages() = storageManager.clearStorages()

  private fun loadProjectFromTemplate(defaultProject: Project) {
    val element = (defaultProject.stateStore as DefaultProjectStoreImpl).getStateCopy() ?: return
    runCatching {
      val dotIdea = dotIdea
      if (dotIdea != null) {
        normalizeDefaultProjectElement(defaultProject, element, dotIdea)
      }
      else {
        moveComponentConfiguration(defaultProject, element,
                                   storagePathResolver = { /* doesn't matter, any path will be resolved as projectFilePath (see fileResolver below) */ PROJECT_FILE }) {
          if (it == "workspace.xml") {
            workspacePath
          }
          else {
            dirOrFile!!
          }
        }
      }
    }.getOrLogException(LOG)
  }

  final override fun getProjectBasePath(): Path {
    val path = dirOrFile ?: throw IllegalStateException("setPath was not yet called")
    if (isDirectoryBased) {
      val useParent = System.getProperty("store.basedir.parent.detection", "true").toBoolean() &&
                      (path.fileName?.toString()?.startsWith("${Project.DIRECTORY_STORE_FOLDER}.") ?: false)
      return if (useParent) path.parent.parent else path
    }
    else {
      return path.parent
    }
  }

  override fun getPresentableUrl(): String {
    if (isDirectoryBased) {
      return (dirOrFile ?: throw IllegalStateException("setPath was not yet called")).systemIndependentPath
    }
    else {
      return projectFilePath.systemIndependentPath
    }
  }

  override fun getProjectWorkspaceId() = ProjectIdManager.getInstance(project).id

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
        // we don't load default state in tests as app store does because
        // 1) we should not do it
        // 2) it was so before, so, we preserve old behavior (otherwise RunManager will load template run configurations)
        // load state only if there are existing files
        val componentStoreLoadingEnabled = project.getUserData(IProjectStore.COMPONENT_STORE_LOADING_ENABLED)
        if (if (componentStoreLoadingEnabled == null) !Files.exists(file) else !componentStoreLoadingEnabled) {
          loadPolicy = StateLoadPolicy.NOT_LOAD
        }
        macros.add(Macro(StoragePathMacros.PRODUCT_WORKSPACE_FILE, workspacePath))
      }
    }
    else {
      val dotIdea = file.resolve(Project.DIRECTORY_STORE_FOLDER)
      this.dotIdea = dotIdea

      // PROJECT_CONFIG_DIR must be first macro
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
    val cacheFileName = doGetProjectFileName(
      presentableUrl = presentableUrl.systemIndependentPath,
      name = (presentableUrl.fileName ?: "").toString().removeSuffix(ProjectFileType.DOT_DEFAULT_EXTENSION),
      hashSeparator = ".",
      extensionWithDot = ".xml",
    )
    macros.add(Macro(StoragePathMacros.CACHE_FILE, appSystemDir.resolve("workspace").resolve(cacheFileName)))

    storageManager.setMacros(macros)

    if (template != null) {
      loadProjectFromTemplate(template)
    }

    if (isUnitTestMode) {
      return
    }

    val projectIdManager = ProjectIdManager.getInstance(project)
    var projectWorkspaceId = projectIdManager.id
    if (projectWorkspaceId == null) {
      // do not use the project name as part of id, to ensure a project dir rename does not cause data loss
      projectWorkspaceId = Ksuid.generate()
      projectIdManager.id = projectWorkspaceId
    }

    val productWorkspaceFile = PathManager.getConfigDir()
      .resolve("workspace")
      .resolve("$projectWorkspaceId.xml")
    macros.add(Macro(StoragePathMacros.PRODUCT_WORKSPACE_FILE, productWorkspaceFile))
    storageManager.setMacros(macros)
  }

  override fun <T> getStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation): List<Storage> {
    val storages = stateSpec.storages
    if (isDirectoryBased) {
      if (storages.size == 2 && ApplicationManager.getApplication().isUnitTestMode &&
          isSpecialStorage(storages.first()) &&
          storages[1].path == StoragePathMacros.WORKSPACE_FILE) {
        return listOf(storages.first())
      }

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
        result = mutableListOf(PROJECT_FILE_STORAGE_ANNOTATION)
      }
      result.sortWith(deprecatedComparator)
      if (isDirectoryBased) {
        for (providerFactory in StreamProviderFactory.EP_NAME.getIterable(project)) {
          LOG.runAndLogException {
            // yes, DEPRECATED_PROJECT_FILE_STORAGE_ANNOTATION is not added in this case
            providerFactory?.customizeStorageSpecs(component, storageManager, stateSpec, result!!, operation)?.let { return it }
          }
        }
      }

      // if we create project from default, component state written not to own storage file, but to project file,
      // we don't have time to fix it properly, so, ancient hack restored
      if (!isSpecialStorage(result.first())) {
        result.add(DEPRECATED_PROJECT_FILE_STORAGE_ANNOTATION)
      }
      return result
    }
    else {
      var result: MutableList<Storage>? = null
      // FlexIdeProjectLevelCompilerOptionsHolder, FlexProjectLevelCompilerOptionsHolderImpl and CustomBeanRegistry
      var hasOnlyDeprecatedStorages = true
      for (storage in storages) {
        if (storage.path == PROJECT_FILE || storage.path == StoragePathMacros.WORKSPACE_FILE || isSpecialStorage(storage)) {
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
      return filePath == projectFilePath.systemIndependentPath || filePath == workspacePath.systemIndependentPath
    }
    return VfsUtilCore.isAncestorOrSelf(projectFilePath.parent.systemIndependentPath, file)
  }

  override fun getDirectoryStorePath(ignoreProjectStorageScheme: Boolean) = dotIdea?.systemIndependentPath.nullize()

  final override fun getDirectoryStorePath() = dotIdea

  final override fun reloadStates(componentNames: Set<String>, messageBus: MessageBus) {
    runBatchUpdate(project) {
      reinitComponents(componentNames)
    }
  }
}

private fun isSpecialStorage(storage: Storage) = isSpecialStorage(storage.path)

internal fun isSpecialStorage(collapsedPath: String): Boolean {
  return collapsedPath == StoragePathMacros.CACHE_FILE || collapsedPath == StoragePathMacros.PRODUCT_WORKSPACE_FILE
}