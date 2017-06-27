/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.configurationStore

import com.intellij.configurationStore.StateMap
import com.intellij.configurationStore.StateStorageManager
import com.intellij.configurationStore.StreamProviderFactory
import com.intellij.configurationStore.XmlElementStorage
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StateSplitterEx
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element

internal class ExternalModuleStorage(private val module: Module, storageManager: StateStorageManager) : XmlElementStorage(StoragePathMacros.MODULE_FILE, "module", storageManager.macroSubstitutor, RoamingType.DISABLED) {
  private val manager = StreamProviderFactory.EP_NAME.getExtensions(module.project).first { it is ExternalSystemStreamProviderFactory } as ExternalSystemStreamProviderFactory

  override fun loadLocalData() = manager.readModuleData(module.name)

  override fun createSaveSession(states: StateMap) = object : XmlElementStorageSaveSession<ExternalModuleStorage>(states, this) {
    override fun saveLocally(element: Element?) {
      // our customizeStorageSpecs on write will not return our storage for not applicable module, so, we don't need to check it here
      var name = module.name
      if (ExternalSystemModulePropertyManager.getInstance(module).isMavenized()) {
        // to distinguish because one project can contain modules from different external systems
        name += "@maven"
      }
      if (element == null) {
        manager.moduleStorage.remove(name)
      }
      else {
        manager.moduleStorage.write(name, element)
      }
    }
  }
}

// for libraries only for now - we use null rootElementName because the only component is expected (libraryTable)
internal class ExternalProjectStorage(fileSpec: String, project: Project, storageManager: StateStorageManager) : XmlElementStorage(fileSpec, null, storageManager.macroSubstitutor, RoamingType.DISABLED) {
  private val manager = StreamProviderFactory.EP_NAME.getExtensions(project).first { it is ExternalSystemStreamProviderFactory } as ExternalSystemStreamProviderFactory

  override fun loadLocalData() = manager.fileStorage.read(fileSpec)

  override fun createSaveSession(states: StateMap) = object : XmlElementStorageSaveSession<ExternalProjectStorage>(states, this) {
    override fun saveLocally(element: Element?) {
      var isEmpty = true
      if (element != null) {
        for (child in element.children) {
          if (child.getAttribute(StateSplitterEx.EXTERNAL_SYSTEM_ID_ATTRIBUTE) != null) {
            isEmpty = false
            break
          }
        }
      }

      if (element == null || isEmpty) {
        manager.fileStorage.remove(fileSpec)
      }
      else {
        manager.fileStorage.write(fileSpec, element, JDOMUtil.ElementOutputFilter { childElement, level -> level != 1 || childElement.getAttribute(StateSplitterEx.EXTERNAL_SYSTEM_ID_ATTRIBUTE) != null })
      }
    }
  }
}