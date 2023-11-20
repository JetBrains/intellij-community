// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalSystem

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.java.workspace.entities.javaSettings
import com.intellij.java.workspace.entities.modifyEntity
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService
import com.intellij.openapi.externalSystem.service.project.manage.WorkspaceDataService
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.MutableEntityStorage

internal class JarTaskManifestDataService : WorkspaceDataService<JarTaskManifestData> {
  private val GRADLE_MAIN_SUFFIX = ".main"
  override fun getTargetDataKey(): Key<JarTaskManifestData> = JarTaskManifestData.KEY

  override fun importData(
    toImport: Collection<DataNode<JarTaskManifestData>>,
    projectData: ProjectData?,
    project: Project,
    mutableStorage: MutableEntityStorage
  ) {
    for (jarTaskManifestNode in toImport) {
      val moduleNode = jarTaskManifestNode.getParent(ModuleData::class.java) ?: continue
      val module = moduleNode.getUserData(AbstractModuleDataService.MODULE_KEY) ?: continue
      val manifestAttributes = jarTaskManifestNode.data.manifestAttributes

      importManifestAttributes(module, manifestAttributes, mutableStorage)
    }
  }

  private fun importManifestAttributes(module: Module, manifestAttributes: Map<String, String>, mutableStorage: MutableEntityStorage) {
    val moduleName = module.name + GRADLE_MAIN_SUFFIX

    val moduleEntity = mutableStorage.resolve(ModuleId(moduleName)) ?: return
    val javaSettings = moduleEntity.javaSettings
    if (javaSettings != null) {
      mutableStorage.modifyEntity(javaSettings) {
        this.manifestAttributes = manifestAttributes
      }
    }
    else {
      mutableStorage addEntity JavaModuleSettingsEntity(inheritedCompilerOutput = true,
                                                        excludeOutput = true,
                                                        entitySource = moduleEntity.entitySource) {
        this.manifestAttributes = manifestAttributes
        this.module = moduleEntity
      }
    }
  }
}

class JarTaskManifestData(val manifestAttributes: Map<String, String>) {
  companion object {
    @JvmField
    val KEY = Key.create(JarTaskManifestData::class.java, ProjectKeys.TASK.processingWeight + 1)
  }
}