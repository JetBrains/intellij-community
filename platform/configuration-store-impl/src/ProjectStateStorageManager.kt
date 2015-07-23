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

import com.intellij.openapi.components.*
import com.intellij.openapi.project.impl.ProjectImpl
import org.jdom.Element

class ProjectStateStorageManager(macroSubstitutor: TrackingPathMacroSubstitutor, private val project: ProjectImpl) : StateStorageManagerImpl(macroSubstitutor, "project", project, project.getPicoContainer()) {
  override fun createStorageData(fileSpec: String, filePath: String) = ProjectStorageData(rootTagName)

  override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String? {
    val workspace = project.isWorkspaceComponent(component.javaClass)
    var fileSpec = if (workspace) StoragePathMacros.WORKSPACE_FILE else StoragePathMacros.PROJECT_FILE
    val storage = getStateStorage(fileSpec, if (workspace) RoamingType.DISABLED else RoamingType.PER_USER)
    if (operation === StateStorageOperation.READ && storage != null && workspace && !storage.hasState(component, componentName, javaClass<Element>(), false)) {
      fileSpec = StoragePathMacros.PROJECT_FILE
    }
    return fileSpec
  }

  override fun createStorageTopicListener() = project.getMessageBus().syncPublisher(StateStorage.PROJECT_STORAGE_TOPIC)
}