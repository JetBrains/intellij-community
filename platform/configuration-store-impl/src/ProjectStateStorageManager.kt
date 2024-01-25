// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.PathMacroSubstitutor
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.ComponentManagerImpl
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

// extended in upsource
open class ProjectStateStorageManager(macroSubstitutor: PathMacroSubstitutor,
                                      private val project: Project,
                                      useVirtualFileTracker: Boolean = true) : StateStorageManagerImpl(ROOT_TAG_NAME, macroSubstitutor, if (useVirtualFileTracker) project else null) {
  companion object {
    internal const val VERSION_OPTION = "version"
    const val ROOT_TAG_NAME = "project"
  }

  private val fileBasedStorageConfiguration = object : FileBasedStorageConfiguration {
    override val isUseVfsForWrite: Boolean
      get() = true

    override val isUseVfsForRead: Boolean
      get() = project is VirtualFileResolver

    override fun resolveVirtualFile(path: String, reasonOperation: StateStorageOperation): VirtualFile? {
      return when (project) {
        is VirtualFileResolver -> project.resolveVirtualFile(path, reasonOperation)
        else -> super.resolveVirtualFile(path, reasonOperation)
      }
    }
  }

  override fun getFileBasedStorageConfiguration(fileSpec: String): FileBasedStorageConfiguration {
    return when {
      isSpecialStorage(fileSpec) -> appFileBasedStorageConfiguration
      else -> fileBasedStorageConfiguration
    }
  }

  override fun normalizeFileSpec(fileSpec: String) = removeMacroIfStartsWith(super.normalizeFileSpec(fileSpec), PROJECT_CONFIG_DIR)

  override fun expandMacro(collapsedPath: String): Path {
    if (collapsedPath[0] == '$') {
      return super.expandMacro(collapsedPath)
    }
    else {
      // PROJECT_CONFIG_DIR is the first macro
      return macros.get(0).value.resolve(collapsedPath)
    }
  }

  override fun beforeElementSaved(elements: MutableList<Element>, rootAttributes: MutableMap<String, String>) {
    rootAttributes.put(VERSION_OPTION, "4")
  }

  override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String {
    if (ComponentManagerImpl.badWorkspaceComponents.contains(componentName)) {
      return StoragePathMacros.WORKSPACE_FILE
    }
    else {
      return PROJECT_FILE
    }
  }

  override val isExternalSystemStorageEnabled: Boolean
    get() = project.isExternalStorageEnabled
}

// for upsource
@ApiStatus.Experimental
interface VirtualFileResolver {
  fun resolveVirtualFile(path: String, reasonOperation: StateStorageOperation): VirtualFile?
}
