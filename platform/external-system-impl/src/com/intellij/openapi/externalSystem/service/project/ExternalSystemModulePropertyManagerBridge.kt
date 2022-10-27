// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModuleEntity
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ExternalSystemModuleOptionsEntity
import com.intellij.workspaceModel.storage.bridgeEntities.getOrCreateExternalSystemModuleOptions

class ExternalSystemModulePropertyManagerBridge(private val module: Module) : ExternalSystemModulePropertyManager() {
  private fun findEntity(): ExternalSystemModuleOptionsEntity? {
    val modelsProvider = module.getUserData(IdeModifiableModelsProviderImpl.MODIFIABLE_MODELS_PROVIDER_KEY)
    val storage = if (modelsProvider != null) modelsProvider.actualStorageBuilder else (module as ModuleBridge).entityStorage.current
    val moduleEntity = (module as ModuleBridge).findModuleEntity(storage)
    return moduleEntity?.exModuleOptions
  }

  @Synchronized
  private fun editEntity(action: ExternalSystemModuleOptionsEntity.Builder.() -> Unit) {
    editEntity(getModuleDiff(), action)
  }

  @Synchronized
  private fun editEntity(moduleDiff: MutableEntityStorage?, action: ExternalSystemModuleOptionsEntity.Builder.() -> Unit) {
    module as ModuleBridge
    if (moduleDiff != null) {
      val moduleEntity = module.findModuleEntity(moduleDiff) ?: return
      val options = moduleDiff.getOrCreateExternalSystemModuleOptions(moduleEntity, moduleEntity.entitySource)
      moduleDiff.modifyEntity(ExternalSystemModuleOptionsEntity.Builder::class.java, options, action)
    }
    else {
      WriteAction.runAndWait<RuntimeException> {
        WorkspaceModel.getInstance(module.project).updateProjectModel("Modify external system module options") { builder ->
          val moduleEntity = module.findModuleEntity(builder) ?: return@updateProjectModel
          val options = builder.getOrCreateExternalSystemModuleOptions(moduleEntity, moduleEntity.entitySource)
          builder.modifyEntity(ExternalSystemModuleOptionsEntity.Builder::class.java, options, action)
        }
      }
    }
  }

  @Synchronized
  private fun updateSource() {
    updateSource(getModuleDiff())
  }

  @Synchronized
  private fun updateSource(storageBuilder: MutableEntityStorage?) {
    module as ModuleBridge
    val storage = storageBuilder ?: module.entityStorage.current
    val moduleEntity = module.findModuleEntity(storage) ?: return
    val externalSystemId = moduleEntity.exModuleOptions?.externalSystem
    val entitySource = moduleEntity.entitySource
    if (externalSystemId == null && entitySource is JpsFileEntitySource ||
        externalSystemId != null && (entitySource as? JpsImportedEntitySource)?.externalSystemId == externalSystemId &&
        entitySource.storedExternally == module.project.isExternalStorageEnabled ||
        entitySource !is JpsFileEntitySource && entitySource !is JpsImportedEntitySource) {
      return
    }
    val newSource = if (externalSystemId == null) {
      (entitySource as JpsImportedEntitySource).internalFile
    }
    else {
      val internalFile = entitySource as? JpsFileEntitySource ?: (entitySource as JpsImportedEntitySource).internalFile
      JpsImportedEntitySource(internalFile, externalSystemId, module.project.isExternalStorageEnabled)
    }
    ModuleManagerBridgeImpl.changeModuleEntitySource(module, storage, newSource, storageBuilder)
  }

  override fun getExternalSystemId(): String? = findEntity()?.externalSystem
  override fun getExternalModuleType(): String? = findEntity()?.externalSystemModuleType
  override fun getExternalModuleVersion(): String? = findEntity()?.externalSystemModuleVersion
  override fun getExternalModuleGroup(): String? = findEntity()?.externalSystemModuleGroup
  override fun getLinkedProjectId(): String? = findEntity()?.linkedProjectId
  override fun getRootProjectPath(): String? = findEntity()?.rootProjectPath
  override fun getLinkedProjectPath(): String? = findEntity()?.linkedProjectPath
  override fun isMavenized(): Boolean = getExternalSystemId() == ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID

  override fun setMavenized(mavenized: Boolean) {
    setMavenized(mavenized, getModuleDiff())
  }

  fun setMavenized(mavenized: Boolean, storageBuilder: MutableEntityStorage?) {
    if (mavenized) {
      unlinkExternalOptions(storageBuilder)
    }
    editEntity(storageBuilder) {
      externalSystem = if (mavenized) ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID else null
    }
    updateSource(storageBuilder)
  }

  override fun unlinkExternalOptions() {
    unlinkExternalOptions(getModuleDiff())
  }

  private fun unlinkExternalOptions(storageBuilder: MutableEntityStorage?) {
    editEntity(storageBuilder) {
      externalSystem = null
      externalSystemModuleVersion = null
      externalSystemModuleGroup = null
      linkedProjectId = null
      linkedProjectPath = null
      rootProjectPath = null
    }
    updateSource(storageBuilder)
  }

  override fun setExternalOptions(id: ProjectSystemId, moduleData: ModuleData, projectData: ProjectData?) {
    editEntity {
      externalSystem = id.toString()
      linkedProjectId = moduleData.id
      linkedProjectPath = moduleData.linkedExternalProjectPath
      rootProjectPath = projectData?.linkedExternalProjectPath ?: ""

      externalSystemModuleGroup = moduleData.group
      externalSystemModuleVersion = moduleData.version
    }
    updateSource()
  }

  override fun setExternalId(id: ProjectSystemId) {
    editEntity {
      externalSystem = id.id
    }
    updateSource()
  }

  override fun setLinkedProjectPath(path: String?) {
    editEntity {
      linkedProjectPath = path
    }
  }

  override fun setRootProjectPath(path: String?) {
    editEntity {
      rootProjectPath = path
    }
  }

  override fun setExternalModuleType(type: String?) {
    editEntity {
      externalSystemModuleType = type
    }
  }

  override fun swapStore() {
  }

  private fun getModuleDiff(): MutableEntityStorage? {
    val modelsProvider = module.getUserData(IdeModifiableModelsProviderImpl.MODIFIABLE_MODELS_PROVIDER_KEY)
    return if (modelsProvider != null) modelsProvider.actualStorageBuilder else (module as ModuleBridge).diff as? MutableEntityStorage
  }
}