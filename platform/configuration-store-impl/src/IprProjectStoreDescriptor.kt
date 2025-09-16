// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import java.nio.file.Path

internal class IprProjectStoreDescriptor(
  private val userBaseDir: Path,
  private val file: Path,
) : ProjectStoreDescriptor {
  override val projectIdentityFile: Path
    get() = userBaseDir
  override val historicalProjectBasePath: Path
    get() = userBaseDir
  override val isDirectoryBased: Boolean
    get() = false

  override val dotIdea: Path?
    get() = null

  override val presentableUrl: Path
    get() = file

  override fun getProjectName(): String {
    return file.fileName.toString().removeSuffix(ProjectFileType.DOT_DEFAULT_EXTENSION)
  }

  override suspend fun saveProjectName(project: Project) {
  }

  override fun getJpsBridgeAwareStorageSpec(filePath: String, project: Project): Storage {
    return doGetJpsBridgeAwareStorageSpec(filePath, project)
  }

  override fun <T : Any> getStorageSpecs(
    component: PersistentStateComponent<T>,
    stateSpec: State,
    operation: StateStorageOperation,
    storageManager: StateStorageManager,
  ): List<Storage> {
    val result = mutableListOf<Storage>()
    var hasOnlyDeprecatedStorages = true
    for (storage in stateSpec.storages) {
      if (storage.path == StoragePathMacros.PROJECT_FILE || storage.path == StoragePathMacros.WORKSPACE_FILE || isSpecialStorage (storage.path)) {
        result.add(storage)
        if (!storage.deprecated) {
          hasOnlyDeprecatedStorages = false
        }
      }
    }
    if (result.isEmpty()) {
      return listOf(FileStorageAnnotation.PROJECT_FILE_STORAGE_ANNOTATION)
    }
    else {
      if (hasOnlyDeprecatedStorages) {
        result.add(FileStorageAnnotation.PROJECT_FILE_STORAGE_ANNOTATION)
      }
      result.sortWith(deprecatedStorageComparator)
      return result
    }
  }
}