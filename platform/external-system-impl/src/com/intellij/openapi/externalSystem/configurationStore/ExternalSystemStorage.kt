// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.configurationStore

import com.intellij.configurationStore.*
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StateSplitterEx
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import org.jdom.Element

internal class ExternalModuleStorage(private val module: Module, storageManager: StateStorageManager) : XmlElementStorage(StoragePathMacros.MODULE_FILE, "module", storageManager.macroSubstitutor, RoamingType.DISABLED) {
  private val manager = StreamProviderFactory.EP_NAME.getExtensions(module.project).first { it is ExternalSystemStreamProviderFactory } as ExternalSystemStreamProviderFactory

  override fun loadLocalData() = manager.readModuleData(module.name)

  override fun createSaveSession(states: StateMap) = object : XmlElementStorageSaveSession<ExternalModuleStorage>(states, this) {
    override fun saveLocally(element: Element?) {
      manager.moduleStorage.write(module.name, element)
    }
  }
}

internal open class ExternalProjectStorage @JvmOverloads constructor(fileSpec: String, project: Project, storageManager: StateStorageManager, rootElementName: String? = ProjectStateStorageManager.ROOT_TAG_NAME /* several components per file */) : XmlElementStorage(fileSpec, rootElementName, storageManager.macroSubstitutor, RoamingType.DISABLED) {
  protected val manager = StreamProviderFactory.EP_NAME.getExtensions(project).first { it is ExternalSystemStreamProviderFactory } as ExternalSystemStreamProviderFactory

  override final fun loadLocalData() = manager.fileStorage.read(fileSpec)

  override fun createSaveSession(states: StateMap) = object : XmlElementStorageSaveSession<ExternalProjectStorage>(states, this) {
    override fun saveLocally(element: Element?) {
      manager.fileStorage.write(fileSpec, element)
    }
  }
}

// for libraries only for now - we use null rootElementName because the only component is expected (libraryTable)
internal class ExternalProjectFilteringStorage(fileSpec: String, project: Project, storageManager: StateStorageManager) : ExternalProjectStorage(fileSpec, project, storageManager, null /* the only component per file */) {
  override fun createSaveSession(states: StateMap) = object : XmlElementStorageSaveSession<ExternalProjectStorage>(states, this) {
    override fun saveLocally(element: Element?) {
      if (element == null || !element.children.any { it.isMarkedAsExternal() }) {
        manager.fileStorage.remove(fileSpec)
      }
      else {
        manager.fileStorage.write(fileSpec, element, JDOMUtil.ElementOutputFilter { childElement, level -> level != 1 || childElement.isMarkedAsExternal() })
      }
    }
  }
}

private fun Element.isMarkedAsExternal() = getAttribute(StateSplitterEx.EXTERNAL_SYSTEM_ID_ATTRIBUTE) != null