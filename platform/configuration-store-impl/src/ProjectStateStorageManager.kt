// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.TrackingPathMacroSubstitutor
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.project.isExternalStorageEnabled
import org.jdom.Element

// extended in upsource
open class ProjectStateStorageManager(macroSubstitutor: TrackingPathMacroSubstitutor,
                                      private val project: ProjectImpl,
                                      useVirtualFileTracker: Boolean = true) : StateStorageManagerImpl(ROOT_TAG_NAME, macroSubstitutor, if (useVirtualFileTracker) project else null) {
  companion object {
    internal const val VERSION_OPTION = "version"
    const val ROOT_TAG_NAME: String = "project"
  }

  override fun normalizeFileSpec(fileSpec: String): String = removeMacroIfStartsWith(super.normalizeFileSpec(fileSpec), PROJECT_CONFIG_DIR)

  override fun expandMacros(path: String): String {
    if (path[0] == '$') {
      return super.expandMacros(path)
    }
    else {
      return "${expandMacro(PROJECT_CONFIG_DIR)}/$path"
    }
  }

  override fun beforeElementSaved(elements: MutableList<Element>, rootAttributes: MutableMap<String, String>) {
    rootAttributes.put(VERSION_OPTION, "4")
  }

  override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String? {
    val workspace = project.isWorkspaceComponent(component.javaClass)
    if (workspace && (operation != StateStorageOperation.READ || getOrCreateStorage(StoragePathMacros.WORKSPACE_FILE, RoamingType.DISABLED).hasState(componentName, false))) {
      return StoragePathMacros.WORKSPACE_FILE
    }
    return PROJECT_FILE
  }

  override val isExternalSystemStorageEnabled: Boolean
    get() = project.isExternalStorageEnabled
}