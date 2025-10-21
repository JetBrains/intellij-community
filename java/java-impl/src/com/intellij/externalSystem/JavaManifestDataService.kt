// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalSystem

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.java.workspace.entities.javaSettings
import com.intellij.java.workspace.entities.modifyJavaModuleSettingsEntity
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService
import com.intellij.openapi.externalSystem.service.project.manage.WorkspaceDataService
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus

private const val GRADLE_MAIN_SUFFIX = ".main"

@ApiStatus.Internal
public class JavaManifestDataService : WorkspaceDataService<JavaManifestData> {

  override fun getTargetDataKey(): Key<JavaManifestData> = JavaManifestData.KEY

  override fun importData(
    toImport: Collection<DataNode<JavaManifestData>>,
    projectData: ProjectData?,
    project: Project,
    mutableStorage: MutableEntityStorage,
  ) {
    for (manifestNode in toImport) {
      val moduleNode = manifestNode.getParent(ModuleData::class.java) ?: continue
      val module = moduleNode.getUserData(AbstractModuleDataService.MODULE_KEY) ?: continue
      val manifestAttributes = manifestNode.data.manifestAttributes

      importManifestAttributes(module, manifestAttributes, mutableStorage)
    }
  }

  private fun importManifestAttributes(module: Module, manifestAttributes: Map<String, String>, mutableStorage: MutableEntityStorage) {
    val moduleName = module.name + GRADLE_MAIN_SUFFIX

    val moduleEntity = mutableStorage.resolve(ModuleId(moduleName)) ?: return
    val javaSettings = moduleEntity.javaSettings
    if (javaSettings != null) {
      mutableStorage.modifyJavaModuleSettingsEntity(javaSettings) {
        this.manifestAttributes = manifestAttributes
      }
    }
    else {
      mutableStorage.modifyModuleEntity(moduleEntity) {
        this.javaSettings = JavaModuleSettingsEntity(inheritedCompilerOutput = true,
                                                     excludeOutput = true,
                                                     entitySource = moduleEntity.entitySource) {
          this.manifestAttributes = manifestAttributes
        }
      }
    }
  }
}