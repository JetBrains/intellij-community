// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.configurationStore

import com.intellij.configurationStore.DataWriter
import com.intellij.configurationStore.DataWriterFilter
import com.intellij.configurationStore.DataWriterFilter.ElementLevel
import com.intellij.configurationStore.DirectoryBasedStorage
import com.intellij.configurationStore.ExternalStorageWithInternalPart
import com.intellij.configurationStore.SaveSessionProducer
import com.intellij.configurationStore.StateMap
import com.intellij.configurationStore.StateStorageManager
import com.intellij.configurationStore.StreamProviderFactory
import com.intellij.configurationStore.XmlElementStorage
import com.intellij.configurationStore.XmlElementStorage.XmlElementStorageSaveSessionProducer
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.settings.SettingsController
import org.jdom.Element
import org.jetbrains.jps.model.serialization.SerializationConstants

internal class ExternalModuleStorage(private val module: Module, storageManager: StateStorageManager)
  : XmlElementStorage(
  fileSpec = StoragePathMacros.MODULE_FILE,
  rootElementName = "module",
  pathMacroSubstitutor = storageManager.macroSubstitutor,
  storageRoamingType = RoamingType.DISABLED,
) {
  override val controller: SettingsController?
    get() = null

  private val manager: ExternalSystemStreamProviderFactory = findExternalSystemStreamProviderFactory(module.project)

  override fun loadLocalData(): Element? = manager.readModuleData(module.name)

  override fun createSaveSession(states: StateMap): SaveSessionProducer {
    return ExternalStorageSaveSessionProducer(states = states, storage = this, fileStorage = manager.moduleStorage, name = module.name)
  }
}

internal open class ExternalProjectStorage(
  fileSpec: String,
  project: Project,
  storageManager: StateStorageManager,
  rootElementName: String?  // several components per file when not null
) : XmlElementStorage(fileSpec, rootElementName, storageManager.macroSubstitutor, RoamingType.DISABLED) {
  override val controller: SettingsController?
    get() = null

  internal val manager: ExternalSystemStreamProviderFactory = findExternalSystemStreamProviderFactory(project)

  override fun loadLocalData(): Element? = manager.fileStorage.read(fileSpec)

  override fun createSaveSession(states: StateMap): SaveSessionProducer {
    return ExternalStorageSaveSessionProducer(states = states, storage = this, fileStorage = manager.fileStorage, name = fileSpec)
  }

  override fun toString(): String = "ExternalProjectStorage(fileSpec=${fileSpec})"
}

// for libraries only for now - we use null rootElementName because the only component is expected (libraryTable)
internal class ExternalProjectFilteringStorage(
  fileSpec: String,
  project: Project,
  storageManager: StateStorageManager,
  private val componentName: String,
  override val internalStorage: DirectoryBasedStorage
) : ExternalProjectStorage(fileSpec, project, storageManager, rootElementName = null), ExternalStorageWithInternalPart {
  private val filter = object : DataWriterFilter {
    private val elementOutputFilter = JDOMUtil.ElementOutputFilter { childElement, level ->
      level != ElementLevel.FIRST.ordinal || childElement.getAttribute(SerializationConstants.EXTERNAL_SYSTEM_ID_ATTRIBUTE) != null
    }

    override fun toElementFilter(): JDOMUtil.ElementOutputFilter = elementOutputFilter

    override fun hasData(element: Element): Boolean = element.children.any { elementOutputFilter.accept(it, 1) }
  }

  override fun loadLocalData(): Element? {
    return JDOMUtil.merge(
      super.loadLocalData(),
      internalStorage.getSerializedState(internalStorage.loadData(), component = null, componentName, archive = true)
    )
  }

  override fun createSaveSession(states: StateMap): SaveSessionProducer {
    return ExternalStorageSaveSessionProducer(
      states = states,
      storage = this,
      fileStorage = manager.fileStorage,
      name = fileSpec,
      filter = filter,
    )
  }
}

private fun findExternalSystemStreamProviderFactory(project: Project): ExternalSystemStreamProviderFactory {
  return StreamProviderFactory.EP_NAME.getExtensions(project)
    .first { it is ExternalSystemStreamProviderFactory } as ExternalSystemStreamProviderFactory
}

private class ExternalStorageSaveSessionProducer(
  states: StateMap,
  storage: XmlElementStorage,
  private val fileStorage: FileSystemExternalSystemStorage,
  private val name: String,
  private val filter: DataWriterFilter? = null
) : XmlElementStorageSaveSessionProducer<XmlElementStorage>(states, storage) {
  override fun remove(events: MutableList<VFileEvent>?) {
    fileStorage.write(name, null, filter)
  }

  override fun saveLocally(dataWriter: DataWriter, events: MutableList<VFileEvent>?) {
    fileStorage.write(name, dataWriter, filter)
  }
}
