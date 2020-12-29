// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.NlsSafe

abstract class ExternalSystemModulePropertyManager {
  abstract fun getExternalSystemId(): String?
  abstract fun getExternalModuleType(): String?
  abstract fun getExternalModuleVersion(): String?
  abstract fun getExternalModuleGroup(): String?
  @NlsSafe abstract fun getLinkedProjectId(): String?
  abstract fun getRootProjectPath(): String?
  abstract fun getLinkedProjectPath(): String?

  abstract fun isMavenized(): Boolean
  abstract fun setMavenized(mavenized: Boolean)

  abstract fun swapStore()
  abstract fun unlinkExternalOptions()
  abstract fun setExternalOptions(id: ProjectSystemId, moduleData: ModuleData, projectData: ProjectData?)
  abstract fun setExternalId(id: ProjectSystemId)
  abstract fun setLinkedProjectPath(path: String?)
  abstract fun setRootProjectPath(path: String?)
  abstract fun setExternalModuleType(type: String?)

  companion object {
    @JvmStatic
    fun getInstance(module: Module): ExternalSystemModulePropertyManager = module.getService(ExternalSystemModulePropertyManager::class.java)!!
  }
}