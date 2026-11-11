// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.configurationStore.schemeManager.ROOT_CONFIG
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.StateSplitterEx
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.ComponentStorageUtil
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.system.OS
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.NoSuchFileException
import java.nio.file.Path

// the component name of `ProjectIdManagerImpl`
private const val PROJECT_ID_COMPONENT = "ProjectId"
private const val PROJECT_ID_ATTRIBUTE = "id"

/**
 * The settings directories of an IDE that is not this process. The IDE can run on another machine.
 *
 * @param configDir the config directory of the IDE
 * @param dotIdea the directory with the project files of a directory-based project
 * @param os the OS of the machine that wrote the settings
 */
@ApiStatus.Internal
class ForeignSettingsRoots(
  @JvmField val configDir: Path?,
  @JvmField val dotIdea: Path?,
  @JvmField val os: OS,
)

/**
 * Reads the state of a [PersistentStateComponent] from the settings files of another IDE.
 * It uses the [State] annotation of the component class only. It creates no component and no storage.
 */
@ApiStatus.Internal
object ForeignStateReader {
  /**
   * Returns the files that can hold the state of [componentClass], in the order in which the component store reads them.
   * A file of a deprecated storage comes after the other files.
   * The result has no file for a directory storage, for a custom storage class, or for a macro that [roots] cannot expand.
   */
  @Throws(IOException::class)
  fun candidateFiles(componentClass: Class<out PersistentStateComponent<*>>, isProjectLevel: Boolean, roots: ForeignSettingsRoots): List<Path> {
    val stateSpec = getStateSpecOrError(componentClass)
    if (stateSpec.storages.isEmpty()) {
      return emptyList()
    }

    val storages: List<Storage>
    val macros: List<Macro>
    if (isProjectLevel) {
      storages = sortStoragesByDeprecated(stateSpec.storages.asList())
      macros = projectMacros(storages, roots)
    }
    else {
      storages = getStorageSpecGenericImpl(stateSpec, componentClass, roots.os)
      macros = appMacros(roots)
    }
    if (macros.isEmpty()) {
      return emptyList()
    }

    return storages.mapNotNull { storage ->
      val fileSpec = FileUtilRt.toSystemIndependentName(storage.path)
      val file = when {
        storage.stateSplitter.java != StateSplitterEx::class.java || storage.storageClass.java != StateStorage::class.java -> null
        fileSpec.startsWith('$') -> expandMacroOrNull(macros, fileSpec)
        // as in the storage managers, a path without a macro is relative to the first macro
        else -> macros.first().value.resolve(fileSpec)
      }
      if (file == null) {
        LOG.debug { "${stateSpec.name}: the storage '$fileSpec' is not supported" }
      }
      file
    }
  }

  /**
   * Returns the state of [componentClass] from the first file of [candidateFiles] that holds it, or `null` if no file holds it.
   * A missing file holds no state. Each other I/O failure is an [IOException], so the caller can try again later.
   */
  @Throws(IOException::class)
  fun <S : Any> readState(componentClass: Class<out PersistentStateComponent<S>>, isProjectLevel: Boolean, roots: ForeignSettingsRoots): S? {
    val componentName = getStateSpecOrError(componentClass).name
    val stateClass = ComponentSerializationUtil.getStateClass<S>(componentClass)
    for (file in candidateFiles(componentClass, isProjectLevel, roots)) {
      val element = readComponentElement(file, componentName) ?: continue
      return deserializeState(element, stateClass)
    }
    return null
  }

  // the macros of `ApplicationStoreImpl.setPath` that have a file in the config directory
  private fun appMacros(roots: ForeignSettingsRoots): List<Macro> {
    val configDir = roots.configDir ?: return emptyList()
    return listOf(Macro(APP_CONFIG, configDir.resolve(PathManager.OPTIONS_DIRECTORY)), Macro(ROOT_CONFIG, configDir))
  }

  // the macros of `ProjectStoreImpl.setPath` for a directory-based project, without `PROJECT_FILE`, as in `DotIdeaProjectStoreDescriptor`
  @Throws(IOException::class)
  private fun projectMacros(storages: List<Storage>, roots: ForeignSettingsRoots): List<Macro> {
    val dotIdea = roots.dotIdea ?: return emptyList()
    val workspaceFile = dotIdea.resolve(WORKSPACE_XML_FILE_NAME)
    val macros = mutableListOf(Macro(PROJECT_CONFIG_DIR, dotIdea), Macro(StoragePathMacros.WORKSPACE_FILE, workspaceFile))
    val configDir = roots.configDir
    // the workspace file is read only for a component that needs the id of the project
    if (configDir != null && storages.any { it.path == StoragePathMacros.PRODUCT_WORKSPACE_FILE }) {
      val id = readComponentElement(workspaceFile, PROJECT_ID_COMPONENT)?.getAttributeValue(PROJECT_ID_ATTRIBUTE)
      val projectId = id?.let { ProjectWorkspaceId.parseOrNull(it) }
      if (projectId != null) {
        macros.add(Macro(StoragePathMacros.PRODUCT_WORKSPACE_FILE, configDir.resolve("$CONFIG_WORKSPACE_DIR/${projectId.value}.xml")))
      }
    }
    return macros
  }

  @Throws(IOException::class)
  private fun readComponentElement(file: Path, componentName: String): Element? {
    val text = try {
      ComponentStorageUtil.loadTextContent(file)
    }
    catch (_: NoSuchFileException) {
      return null
    }
    if (text.isBlank()) {
      return null
    }

    val root = try {
      JDOMUtil.load(text)
    }
    catch (e: JDOMException) {
      LOG.warn("Cannot parse $file", e)
      return null
    }
    return ComponentStorageUtil.loadComponents(root, null).get(componentName)
  }
}
