// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.module.Module

interface ExternalSystemModulePropertyManager {
  fun getExternalSystemId(): String?
  fun getExternalModuleType(): String?
  fun getExternalModuleVersion(): String?
  fun getExternalModuleGroup(): String?
  fun getLinkedProjectId(): String?
  fun getRootProjectPath(): String?
  fun getLinkedProjectPath(): String?

  fun isMavenized(): Boolean
  fun setMavenized(mavenized: Boolean)

  fun swapStore()
  fun unlinkExternalOptions()
  fun setExternalOptions(id: ProjectSystemId, moduleData: ModuleData, projectData: ProjectData?)
  fun setExternalId(id: ProjectSystemId)
  fun setLinkedProjectPath(path: String?)
  fun setRootProjectPath(path: String?)
  fun setExternalModuleType(type: String?)

  companion object {
    @JvmStatic
    fun getInstance(module: Module): ExternalSystemModulePropertyManager = module.getService(ExternalSystemModulePropertyManager::class.java)!!
  }
}