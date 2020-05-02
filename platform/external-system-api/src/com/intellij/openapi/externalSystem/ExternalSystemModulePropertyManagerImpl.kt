// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.roots.ProjectModelElement
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Transient
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

val EMPTY_STATE: ExternalStateComponent = ExternalStateComponent()

@Suppress("DEPRECATION")
@State(name = "ExternalSystem")
class ExternalSystemModulePropertyManagerImpl(private val module: Module) : ExternalSystemModulePropertyManager(),
                                                                            PersistentStateComponent<ExternalStateComponent>, ProjectModelElement {
  override fun getExternalSource(): ProjectModelExternalSource? = store.externalSystem?.let {
    ExternalProjectSystemRegistry.getInstance().getSourceById(it)
  }

  private var store = if (module.project.isExternalStorageEnabled) ExternalStateComponent() else ExternalStateModule(module)

  override fun getState(): ExternalStateComponent = store as? ExternalStateComponent ?: EMPTY_STATE

  override fun loadState(state: ExternalStateComponent) {
    store = state
  }

  @Suppress("DEPRECATION")
  override fun getExternalSystemId(): String? = store.externalSystem

  override fun getExternalModuleType(): String? = store.externalSystemModuleType

  override fun getExternalModuleVersion(): String? = store.externalSystemModuleVersion

  override fun getExternalModuleGroup(): String? = store.externalSystemModuleGroup

  override fun getLinkedProjectId(): String? = store.linkedProjectId

  override fun getRootProjectPath(): String? = store.rootProjectPath

  override fun getLinkedProjectPath(): String? = store.linkedProjectPath

  @Suppress("DEPRECATION")
  override fun isMavenized(): Boolean = store.isMavenized

  @Suppress("DEPRECATION")
  override fun setMavenized(mavenized: Boolean) {
    if (mavenized) {
      // clear external system API options
      // see com.intellij.openapi.externalSystem.service.project.manage.ModuleDataService#setModuleOptions
      unlinkExternalOptions()
    }
    // must be after unlinkExternalOptions
    store.isMavenized = mavenized
  }

  override fun swapStore() {
    val oldStore = store
    val isMaven = oldStore.isMavenized
    if (oldStore is ExternalStateModule) {
      store = ExternalStateComponent()
    }
    else {
      store = ExternalStateModule(module)
    }

    store.externalSystem = if (store is ExternalStateModule && isMaven) null else oldStore.externalSystem
    if (store !is ExternalStateComponent) {
      // do not set isMavenized for ExternalStateComponent it just set externalSystem
      store.isMavenized = isMaven
    }

    store.linkedProjectId = oldStore.linkedProjectId
    store.linkedProjectPath = oldStore.linkedProjectPath
    store.rootProjectPath = oldStore.rootProjectPath
    store.externalSystemModuleGroup = oldStore.externalSystemModuleGroup
    store.externalSystemModuleVersion = oldStore.externalSystemModuleVersion

    if (oldStore is ExternalStateModule) {
      oldStore.isMavenized = false
      oldStore.unlinkExternalOptions()
    }
  }

  override fun unlinkExternalOptions() {
    store.unlinkExternalOptions()
  }

  override fun setExternalOptions(id: ProjectSystemId, moduleData: ModuleData, projectData: ProjectData?) {
    // clear maven option, must be first
    store.isMavenized = false

    store.externalSystem = id.toString()
    store.linkedProjectId = moduleData.id
    store.linkedProjectPath = moduleData.linkedExternalProjectPath
    store.rootProjectPath = projectData?.linkedExternalProjectPath ?: ""

    store.externalSystemModuleGroup = moduleData.group
    store.externalSystemModuleVersion = moduleData.version
  }

  override fun setExternalId(id: ProjectSystemId) {
    store.externalSystem = id.id
  }

  override fun setLinkedProjectPath(path: String?) {
    state.linkedProjectPath = path
  }

  override fun setRootProjectPath(path: String?) {
    state.rootProjectPath = path
  }

  override fun setExternalModuleType(type: String?) {
    store.externalSystemModuleType = type
  }
}

private interface ExternalOptionState {
  var externalSystem: String?
  var externalSystemModuleVersion: String?

  var linkedProjectPath: String?
  var linkedProjectId: String?
  var rootProjectPath: String?

  var externalSystemModuleGroup: String?
  var externalSystemModuleType: String?

  var isMavenized: Boolean
}

private fun ExternalOptionState.unlinkExternalOptions() {
  externalSystem = null
  linkedProjectId = null
  linkedProjectPath = null
  rootProjectPath = null
  externalSystemModuleGroup = null
  externalSystemModuleVersion = null
}

@Suppress("DEPRECATION")
private class ModuleOptionDelegate(private val key: String) : ReadWriteProperty<ExternalStateModule, String?> {
  override operator fun getValue(thisRef: ExternalStateModule, property: KProperty<*>) = thisRef.module.getOptionValue(key)

  override operator fun setValue(thisRef: ExternalStateModule, property: KProperty<*>, value: String?) {
    thisRef.module.setOption(key, value)
  }
}

@Suppress("DEPRECATION")
private class ExternalStateModule(internal val module: Module) : ExternalOptionState {
  override var externalSystem by ModuleOptionDelegate(ExternalProjectSystemRegistry.EXTERNAL_SYSTEM_ID_KEY)
  override var externalSystemModuleVersion by ModuleOptionDelegate("external.system.module.version")
  override var externalSystemModuleGroup by ModuleOptionDelegate("external.system.module.group")
  override var externalSystemModuleType by ModuleOptionDelegate("external.system.module.type")

  override var linkedProjectPath by ModuleOptionDelegate("external.linked.project.path")
  override var linkedProjectId by ModuleOptionDelegate("external.linked.project.id")

  override var rootProjectPath by ModuleOptionDelegate("external.root.project.path")

  override var isMavenized: Boolean
    get() = "true" == module.getOptionValue(ExternalProjectSystemRegistry.IS_MAVEN_MODULE_KEY)
    set(value) {
      module.setOption(ExternalProjectSystemRegistry.IS_MAVEN_MODULE_KEY, if (value) "true" else null)
    }
}

class ExternalStateComponent : ExternalOptionState {
  @get:Attribute
  override var externalSystem: String? = null

  @get:Attribute
  override var externalSystemModuleVersion: String? = null

  @get:Attribute
  override var externalSystemModuleGroup: String? = null

  @get:Attribute
  override var externalSystemModuleType: String? = null

  @get:Attribute
  override var linkedProjectPath: String? = null

  @get:Attribute
  override var linkedProjectId: String? = null

  @get:Attribute
  override var rootProjectPath: String? = null

  @get:Transient
  override var isMavenized: Boolean
    get() = externalSystem == ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID
    set(value) {
      externalSystem = if (value) ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID else null
    }
}