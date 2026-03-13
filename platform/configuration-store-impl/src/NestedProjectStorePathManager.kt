// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.diagnostic.PluginException
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.StateSplitterEx
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.ex.ProjectNameProvider
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import com.intellij.util.PathUtilRt
import org.jdom.Element
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path

private val EP_NAME: ExtensionPointName<ProjectStorePathCustomizer> = ExtensionPointName("com.intellij.projectStorePathCustomizer")
private val DEPRECATED_PROJECT_FILE_STORAGE_ANNOTATION = FileStorageAnnotation(StoragePathMacros.PROJECT_FILE, true)

/**
 * For cases when the project configuration store resides in the project root directory - the default
 */
internal class NestedProjectStorePathManager : ProjectStorePathManager {
  override fun getStoreDescriptor(projectRoot: Path): ProjectStoreDescriptor {
    val suitableDescriptors = ArrayList<ProjectStoreDescriptor>()
    for (descriptor in EP_NAME.filterableLazySequence()) {
      val pluginId = descriptor.pluginDescriptor.pluginId
      if (!descriptor.pluginDescriptor.isBundled && pluginId.idString != "org.jetbrains.bazel") {
        LOG.warn(PluginException("ProjectStorePathCustomizer from '${descriptor.pluginDescriptor}' is not allowed (not in a whitelist)", pluginId))
        continue
      }

      val descriptor = descriptor.instance?.getStoreDirectoryPath(projectRoot)
      if (descriptor != null) {
        suitableDescriptors.add(descriptor)
      }
    }

    if (suitableDescriptors.isNotEmpty()) {
      require(suitableDescriptors.size == 1) {
        "More than one suitable ProjectStorePathCustomizer found: ${suitableDescriptors.joinToString()}. " +
        "We cannot determine which one is suitable for $projectRoot"
      }
      return suitableDescriptors.single()
    }

    if (projectRoot.toString().endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
      // null parent - file in the root (for example, memory fs in tests)
      val userBaseDir = projectRoot.parent ?: projectRoot.fileSystem.rootDirectories.first()
      return IprProjectStoreDescriptor(userBaseDir, projectRoot)
    }

    return if (Files.isRegularFile(projectRoot)) {
      DotIdeaProjectStoreDescriptor(
        projectIdentityFile = projectRoot.parent,
        historicalProjectBasePath = projectRoot.parent,
      )
    }
    else if (Files.isDirectory(projectRoot)
             && System.getProperty("store.basedir.parent.detection", "true").toBoolean()
             && (projectRoot.fileName?.toString()?.startsWith("${Project.DIRECTORY_STORE_FOLDER}.") == true)) {
      DotIdeaProjectStoreDescriptor(
        projectIdentityFile = projectRoot,
        historicalProjectBasePath = projectRoot.parent.parent,
      )
    }
    else {
      DotIdeaProjectStoreDescriptor(
        projectIdentityFile = projectRoot,
        historicalProjectBasePath = projectRoot,
      )
    }
  }

  override fun getStoreDirectory(projectRoot: VirtualFile): VirtualFile? {
    return if (projectRoot.isDirectory) projectRoot.findChild(Project.DIRECTORY_STORE_FOLDER) else null
  }
}

private class DotIdeaProjectStoreDescriptor(
  override val projectIdentityFile: Path,
  override val historicalProjectBasePath: Path,
) : ProjectStoreDescriptor {
  private var lastSavedProjectName: String? = null


  override val dotIdea: Path = projectIdentityFile.resolve(Project.DIRECTORY_STORE_FOLDER)

  override fun getJpsBridgeAwareStorageSpec(filePath: String, project: Project): Storage {
    return doGetJpsBridgeAwareStorageSpec(filePath, project)
  }

  override val isExternalStorageSupported: Boolean
    get() = true

  override fun testStoreDirectoryExistsForProjectRoot(): Boolean {
    return Files.isDirectory(dotIdea)
  }

  override fun getModuleStorageSpecs(
    component: PersistentStateComponent<*>,
    stateSpec: State,
    operation: StateStorageOperation,
    storageManager: StateStorageManager,
    project: Project,
  ): List<Storage> {
    val result = if (stateSpec.storages.isEmpty()) {
      listOf(FileStorageAnnotation.MODULE_FILE_STORAGE_ANNOTATION)
    }
    else {
      getStorageSpecGenericImpl(component = component, stateSpec = stateSpec)
    }

    for (provider in StreamProviderFactory.EP_NAME.getExtensions(project)) {
      runCatching {
        provider.customizeStorageSpecs(
          component = component,
          storageManager = storageManager,
          stateSpec = stateSpec,
          storages = result,
          operation = operation,
        )
      }.getOrLogException(LOG)?.let {
        return it
      }
    }

    return result
  }

  override fun <T : Any> getStorageSpecs(
    component: PersistentStateComponent<T>,
    stateSpec: State,
    operation: StateStorageOperation,
    storageManager: StateStorageManager,
  ): List<Storage> {
    val storages = stateSpec.storages
    if (storages.size == 2 && ApplicationManager.getApplication().isUnitTestMode &&
        isSpecialStorage(storages.first().path) &&
        storages[1].path == StoragePathMacros.WORKSPACE_FILE) {
      return listOf(storages.first())
    }

    val result = mutableListOf<Storage>()
    for (storage in storages) {
      if (storage.path != StoragePathMacros.PROJECT_FILE) {
        result.add(storage)
      }
    }
    if (result.isEmpty()) {
      result.add(FileStorageAnnotation.PROJECT_FILE_STORAGE_ANNOTATION)
    }
    else {
      result.sortWith(deprecatedStorageComparator)
    }

    val project = storageManager.componentManager as Project
    for (providerFactory in StreamProviderFactory.EP_NAME.asSequence(project)) {
      val customizedSpecs = runCatching {
        // yes, DEPRECATED_PROJECT_FILE_STORAGE_ANNOTATION is not added in this case
        providerFactory.customizeStorageSpecs(component = component, storageManager = storageManager, stateSpec = stateSpec, storages = result, operation = operation)
      }.getOrLogException(LOG)
      if (customizedSpecs != null) {
        return customizedSpecs
      }
    }

    if (!isSpecialStorage(result.first().path)) {
      // if we create a project from a default template, component state written not to own storage file, but to project file,
      // we don't have time to fix it properly, so, ancient hack restored
      result.add(DEPRECATED_PROJECT_FILE_STORAGE_ANNOTATION)
    }
    return result
  }

  override val projectName: @NlsSafe String
    get() {
      val storedName = JpsPathUtil.readProjectName(dotIdea)
      if (storedName != null) {
        lastSavedProjectName = storedName
        return storedName
      }

      return super.projectName
    }

  override suspend fun saveProjectName(project: Project) {
    try {
      val currentProjectName = project.name
      if (lastSavedProjectName == currentProjectName) {
        return
      }

      lastSavedProjectName = currentProjectName

      val nameFile = getNameFileForDotIdeaProject(project, dotIdea)

      fun doSave() {
        val basePath = historicalProjectBasePath
        if (currentProjectName == basePath.fileName?.toString()) {
          // name equals to base path name - remove name
          Files.deleteIfExists(nameFile)
        }
        else if (Files.isDirectory(basePath)) {
          NioFiles.createParentDirectories(nameFile)
          Files.write(nameFile, currentProjectName.toByteArray())
        }
      }

      try {
        doSave()
      }
      catch (e: AccessDeniedException) {
        val status = ensureFilesWritable(project, listOf(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(nameFile)!!))
        if (status.hasReadonlyFiles()) {
          throw e
        }
        doSave()
      }
    }
    catch (e: Throwable) {
      LOG.error("Unable to store project name", e)
    }
  }
}

internal fun doGetJpsBridgeAwareStorageSpec(filePath: String, project: Project): Storage {
  val collapsedPath: String
  val splitterClass: Class<out StateSplitterEx>
  val fileName = PathUtilRt.getFileName(filePath)
  val parentPath = PathUtilRt.getParentPath(filePath)
  val parentFileName = PathUtilRt.getFileName(parentPath)
  if (filePath.endsWith(".ipr") || (fileName == "misc.xml" && parentFileName == Project.DIRECTORY_STORE_FOLDER)) {
    collapsedPath = $$"$PROJECT_FILE$"
    splitterClass = StateSplitterEx::class.java
  }
  else {
    if (parentFileName == Project.DIRECTORY_STORE_FOLDER) {
      collapsedPath = fileName
      splitterClass = StateSplitterEx::class.java
    }
    else {
      val grandParentPath = PathUtilRt.getParentPath(parentPath)
      collapsedPath = parentFileName
      splitterClass = FakeDirectoryBasedStateSplitter::class.java
      if (PathUtil.getFileName(grandParentPath) != Project.DIRECTORY_STORE_FOLDER) {
        if (parentFileName == "project") {
          if (fileName == "libraries.xml" || fileName == "artifacts.xml") {
            val inProjectStorage = FileStorageAnnotation(FileUtilRt.getNameWithoutExtension(fileName), false, splitterClass)
            val componentName = if (fileName == "libraries.xml") "libraryTable" else "ArtifactManager"
            StreamProviderFactory.EP_NAME.computeSafeIfAny(project) {
              it.getOrCreateStorageSpec(fileName, StateAnnotation(componentName, inProjectStorage))
            }?.let { return it }
          }
          if (fileName == "modules.xml") {
            StreamProviderFactory.EP_NAME.computeSafeIfAny(project) {
              it.getOrCreateStorageSpec(fileName)
            }?.let { return it }
          }
        }
        if (StreamProviderFactory.EP_NAME.hasAnyExtensions(project)) {
          error("$filePath is not under .idea directory and not under external system cache")
        }
      }
    }
  }
  return FileStorageAnnotation(/* path = */ collapsedPath, /* deprecated = */ false, /* splitterClass = */ splitterClass)
}

@VisibleForTesting
internal fun getNameFileForDotIdeaProject(project: Project, dotIdea: Path): Path {
  for (projectNameProvider in ProjectNameProvider.EP_NAME.lazySequence()) {
    runCatching { projectNameProvider.getNameFile(project) }
      .getOrLogException(LOG)
      ?.let { return it }
  }
  return dotIdea.resolve(ProjectEx.NAME_FILE)
}

/**
 * This fake implementation is used to force creating directory-based storage in `StateStorageManagerImpl.createStateStorage`.
 */
private class FakeDirectoryBasedStateSplitter : StateSplitterEx() {
  override fun splitState(state: Element): MutableList<Pair<Element, String>> = throw AssertionError()
}