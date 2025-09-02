// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ExternalSystemModuleOptionsEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.testFramework.workspaceModel.update
import com.intellij.util.asDisposable
import com.intellij.util.io.createDirectories
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class ExternalSystemModuleDataServiceTest : ExternalSystemModuleDataServiceTestCase() {

  @Test
  fun `test create modules`(): Unit = runBlocking {
    val moduleNames = (0 until 10).map { "module$it" }

    val systemId = ProjectSystemId("systemId")
    val manager = createManager(systemId, projectPath)
    ExternalSystemManager.EP_NAME.point.registerExtension(manager, asDisposable())

    val projectData = ProjectData(systemId, "project", projectPath, projectPath)
    val moduleData = moduleNames.map { moduleName ->
      ModuleData(moduleName, systemId, "moduleType", moduleName, "$projectPath/$moduleName.iml", "$projectPath/$moduleName")
    }

    val projectNode = DataNode(ProjectKeys.PROJECT, projectData, null)
    val moduleNodes = moduleData.map { DataNode(ProjectKeys.MODULE, it, projectNode) }

    val modelsProvider = createModelsProvider(asDisposable())

    assertModules(modelsProvider, emptyList())

    importModuleData(modelsProvider, projectData, moduleNodes)

    assertModules(modelsProvider, moduleNames)
    for (moduleName in moduleNames) {
      assertModuleOptions(modelsProvider, moduleName, systemId, projectPath, "$projectPath/$moduleName", moduleName)
    }
  }

  @Test
  fun `test delete modules`(): Unit = runBlocking {
    val moduleNames = (0 until 10).map { "module$it" }
    for (moduleName in moduleNames) {
      projectRoot.resolve(moduleName).createDirectories()
    }

    val systemId = ProjectSystemId("systemId")
    val manager = createManager(systemId, projectPath)
    ExternalSystemManager.EP_NAME.point.registerExtension(manager, asDisposable())

    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    val projectUrl = projectRoot.toVirtualFileUrl(virtualFileUrlManager)

    project.workspaceModel.update { storage ->
      for (moduleName in moduleNames) {
        storage addEntity ModuleEntity(moduleName, emptyList(), NonPersistentEntitySource) {
          contentRoots += ContentRootEntity(projectUrl.append(moduleName), emptyList(), NonPersistentEntitySource)
          exModuleOptions = ExternalSystemModuleOptionsEntity(NonPersistentEntitySource) {
            externalSystem = systemId.id
            rootProjectPath = projectPath
            linkedProjectPath = "$projectPath/$moduleName"
            linkedProjectId = moduleName
          }
        }
      }
    }

    val projectData = ProjectData(systemId, "project", projectPath, projectPath)

    val modelsProvider = createModelsProvider(asDisposable())

    assertModules(modelsProvider, moduleNames)
    for (moduleName in moduleNames) {
      assertContentRoots(modelsProvider, moduleName, listOf("$projectPath/$moduleName"))
      assertModuleOptions(modelsProvider, moduleName, systemId, projectPath, "$projectPath/$moduleName", moduleName)
    }

    importModuleData(modelsProvider, projectData, emptyList())

    assertModules(modelsProvider, emptyList())
  }

  @Test
  fun `test migrate modules`(): Unit = runBlocking {
    val moduleNames = (0 until 10).map { "module$it" }
    for (moduleName in moduleNames) {
      projectRoot.resolve(moduleName).createDirectories()
    }

    val systemId = ProjectSystemId("systemId")
    val manager = createManager(systemId, projectPath)
    ExternalSystemManager.EP_NAME.point.registerExtension(manager, asDisposable())

    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    val projectUrl = projectRoot.toVirtualFileUrl(virtualFileUrlManager)

    project.workspaceModel.update { storage ->
      for (moduleName in moduleNames) {
        storage addEntity ModuleEntity(moduleName, emptyList(), NonPersistentEntitySource) {
          contentRoots += ContentRootEntity(projectUrl.append(moduleName), emptyList(), NonPersistentEntitySource)
          exModuleOptions = null
        }
      }
    }

    val projectData = ProjectData(systemId, "project", projectPath, projectPath)
    val moduleData = moduleNames.map { moduleName ->
      ModuleData(moduleName, systemId, "moduleType", moduleName, "$projectPath/$moduleName.iml", "$projectPath/$moduleName")
    }

    val projectNode = DataNode(ProjectKeys.PROJECT, projectData, null)
    val moduleNodes = moduleData.map { DataNode(ProjectKeys.MODULE, it, projectNode) }

    val modelsProvider = createModelsProvider(asDisposable())

    assertModules(modelsProvider, moduleNames)
    for (moduleName in moduleNames) {
      assertContentRoots(modelsProvider, moduleName, listOf("$projectPath/$moduleName"))
      assertModuleOptions(modelsProvider, moduleName, null, null, null, null)
    }

    importModuleData(modelsProvider, projectData, moduleNodes)

    assertModules(modelsProvider, moduleNames)
    for (moduleName in moduleNames) {
      assertContentRoots(modelsProvider, moduleName, listOf("$projectPath/$moduleName"))
      assertModuleOptions(modelsProvider, moduleName, systemId, projectPath, "$projectPath/$moduleName", moduleName)
    }
  }
}