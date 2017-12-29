/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    const val ROOT_TAG_NAME = "project"
  }

  override fun normalizeFileSpec(fileSpec: String) = removeMacroIfStartsWith(super.normalizeFileSpec(fileSpec), PROJECT_CONFIG_DIR)

  override fun expandMacros(path: String): String {
    if (path[0] == '$') {
      return super.expandMacros(path)
    }
    else {
      return "${expandMacro(PROJECT_CONFIG_DIR)}/$path"
    }
  }

  override fun beforeElementSaved(element: Element) {
    element.setAttribute(VERSION_OPTION, "4")
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