// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.externalSystem.testFramework.project
import com.intellij.platform.workspace.jps.entities.ExternalSystemModuleOptionsEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.testFramework.replaceService
import com.intellij.util.asDisposable
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class ExternalSystemModuleDataIndexTest : ExternalSystemModuleDataIndexTestCase() {

  @Test
  fun `test module data finding`(): Unit = runBlocking {
    val systemId1 = ProjectSystemId("build-tool-1")
    val systemId2 = ProjectSystemId("build-tool-2")

    val manager1 = createManager(systemId1, "$projectPath/project1")
    val manager2 = createManager(systemId2, "$projectPath/project2")

    ExternalSystemManager.EP_NAME.point.registerExtension(manager1, asDisposable())
    ExternalSystemManager.EP_NAME.point.registerExtension(manager2, asDisposable())

    val dataStorage = createDataStorage(
      project(name = "project1", projectPath = "$projectPath/project1", systemId = systemId1) {
        module(name = "project1", externalProjectPath = projectPath)
        module(name = "project1.module1", externalProjectPath = "$projectPath/module1")
        module(name = "project1.module2", externalProjectPath = "$projectPath/module2")
      },
      project(name = "project2", projectPath = "$projectPath/project2", systemId = systemId2) {
        module(name = "project2", externalProjectPath = projectPath)
        module(name = "project2.module1", externalProjectPath = "$projectPath/module1")
        module(name = "project2.module2", externalProjectPath = "$projectPath/module2")
      }
    )

    project.replaceService(ExternalProjectsDataStorage::class.java, dataStorage, asDisposable())

    project.workspaceModel.update("Test description") { storage ->
      storage addEntity ExternalSystemModuleOptionsEntity(ENTITY_SOURCE) {
        module = ModuleEntity("project1", emptyList(), ENTITY_SOURCE)
        linkedProjectId = "project1"
        rootProjectPath = "$projectPath/project1"
        linkedProjectPath = "$projectPath/project1"
      }
      storage addEntity ExternalSystemModuleOptionsEntity(ENTITY_SOURCE) {
        module = ModuleEntity("project1.module1", emptyList(), ENTITY_SOURCE)
        linkedProjectId = "project1:module1"
        rootProjectPath = "$projectPath/project1"
        linkedProjectPath = "$projectPath/project1/module1"
      }
      storage addEntity ExternalSystemModuleOptionsEntity(ENTITY_SOURCE) {
        module = ModuleEntity("project1.module2", emptyList(), ENTITY_SOURCE)
        linkedProjectId = "project1:module2"
        rootProjectPath = "$projectPath/project1"
        linkedProjectPath = "$projectPath/project1/module2"
      }
      storage addEntity ExternalSystemModuleOptionsEntity(ENTITY_SOURCE) {
        module = ModuleEntity("project2", emptyList(), ENTITY_SOURCE)
        linkedProjectId = "project2"
        rootProjectPath = "$projectPath/project2"
        linkedProjectPath = "$projectPath/project2"
      }
      storage addEntity ExternalSystemModuleOptionsEntity(ENTITY_SOURCE) {
        module = ModuleEntity("project2.module1", emptyList(), ENTITY_SOURCE)
        linkedProjectId = "project2:module1"
        rootProjectPath = "$projectPath/project2"
        linkedProjectPath = "$projectPath/project2/module1"
      }
      storage addEntity ExternalSystemModuleOptionsEntity(ENTITY_SOURCE) {
        module = ModuleEntity("project2.module2", emptyList(), ENTITY_SOURCE)
        linkedProjectId = "project2:module2"
        rootProjectPath = "$projectPath/project2"
        linkedProjectPath = "$projectPath/project2/module2"
      }
    }

    assertModules(
      "project1", "project1.module1", "project1.module2",
      "project2", "project2.module1", "project2.module2"
    )
    assertModuleNode(systemId1, "project1", "$projectPath/project1", ExternalSystemModuleDataIndex.findModuleNode(getModule("project1")))
    assertModuleNode(systemId1, "project1.module1", "$projectPath/project1/module1", ExternalSystemModuleDataIndex.findModuleNode(getModule("project1.module1")))
    assertModuleNode(systemId1, "project1.module2", "$projectPath/project1/module2", ExternalSystemModuleDataIndex.findModuleNode(getModule("project1.module2")))
    assertModuleNode(systemId2, "project2", "$projectPath/project2", ExternalSystemModuleDataIndex.findModuleNode(getModule("project2")))
    assertModuleNode(systemId2, "project2.module1", "$projectPath/project2/module1", ExternalSystemModuleDataIndex.findModuleNode(getModule("project2.module1")))
    assertModuleNode(systemId2, "project2.module2", "$projectPath/project2/module2", ExternalSystemModuleDataIndex.findModuleNode(getModule("project2.module2")))
  }
}