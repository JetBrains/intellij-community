// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.ex.ProjectNameProvider
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
private val DEPRECATED_PROJECT_FILE_STORAGE_ANNOTATION = FileStorageAnnotation(PROJECT_FILE, true)

/**
 * For cases when the project configuration store resides in the project root directory - the default
 */
internal class NestedProjectStorePathManager : ProjectStorePathManager {
  override fun getStoreDescriptor(projectRoot: Path): ProjectStoreDescriptor {
    val suitableDescriptors = ArrayList<ProjectStoreDescriptor>()
    for (descriptor in EP_NAME.filterableLazySequence()) {
      val pluginId = descriptor.pluginDescriptor.pluginId
      if (!descriptor.pluginDescriptor.isBundled && pluginId.idString != "org.jetbrains.bazel") {
        thisLogger().warn(
          PluginException("ProjectStorePathCustomizer from '${descriptor.pluginDescriptor}' is not allowed (not in a whitelist)", pluginId),
        )
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

    val useParent = System.getProperty("store.basedir.parent.detection", "true").toBoolean() &&
                    (projectRoot.fileName?.toString()?.startsWith("${Project.DIRECTORY_STORE_FOLDER}.") == true)
    return object : ProjectStoreDescriptor {
      private var lastSavedProjectName: String? = null

      override val projectIdentityDir = projectRoot
      override val historicalProjectBasePath = if (useParent) projectRoot.parent.parent else projectRoot
      override val dotIdea = projectRoot.resolve(Project.DIRECTORY_STORE_FOLDER)

      override fun getJpsBridgeAwareStorageSpec(filePath: String, project: Project): Storage {
        return doGetJpsBridgeAwareStorageSpec(filePath, project)
      }

      override val isExternalStorageSupported: Boolean
        get() = true

      override fun customizeStorageSpecs(
        component: PersistentStateComponent<*>,
        storageManager: StateStorageManager,
        stateSpec: State,
        storages: List<Storage>,
        operation: StateStorageOperation,
      ): List<Storage> {
        val project = storageManager.componentManager as Project
        for (providerFactory in StreamProviderFactory.EP_NAME.asSequence(project)) {
          val customizedSpecs = runCatching {
            // yes, DEPRECATED_PROJECT_FILE_STORAGE_ANNOTATION is not added in this case
            providerFactory.customizeStorageSpecs(component = component, storageManager = storageManager, stateSpec = stateSpec, storages = storages, operation = operation)
          }.getOrLogException(LOG)
          if (customizedSpecs != null) {
            return customizedSpecs
          }
        }

        @Suppress("GrazieInspection")
        if (isSpecialStorage(storages.first().path)) {
          return storages
        }
        else {
          // if we create project from default, component state written not to own storage file, but to project file,
          // we don't have time to fix it properly, so, ancient hack restored
          return storages + DEPRECATED_PROJECT_FILE_STORAGE_ANNOTATION
        }
      }

      override fun getProjectName(): String {
        val storedName = JpsPathUtil.readProjectName(dotIdea)
        if (storedName != null) {
          lastSavedProjectName = storedName
          return storedName
        }

        return NioFiles.getFileName(historicalProjectBasePath)
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
  }

  override fun getStoreDirectory(projectRoot: VirtualFile): VirtualFile? {
    return if (projectRoot.isDirectory) projectRoot.findChild(Project.DIRECTORY_STORE_FOLDER) else null
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