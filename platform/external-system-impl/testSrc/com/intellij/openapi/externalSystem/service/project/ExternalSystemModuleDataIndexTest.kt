// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.platform.externalSystem.testFramework.project
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestDisposable
import org.junit.jupiter.api.Test

class ExternalSystemModuleDataIndexTest : ExternalSystemModuleDataIndexTestCase() {

  @Test
  fun `test module data finding`(@TestDisposable disposable: Disposable) = timeoutRunBlocking {
    val systemId1 = ProjectSystemId("build-tool-1")
    val systemId2 = ProjectSystemId("build-tool-2")

    val manager1 = createManager(systemId1, "$projectPath/project1")
    val manager2 = createManager(systemId2, "$projectPath/project2")

    ExternalSystemManager.EP_NAME.point.registerExtension(manager1, disposable)
    ExternalSystemManager.EP_NAME.point.registerExtension(manager2, disposable)

    importData(
      project(name = "project1", projectPath = "$projectPath/project1", systemId = systemId1) {
        module(name = "project1", externalProjectPath = projectPath)
        module(name = "project1.module1", externalProjectPath = "$projectPath/module1")
        module(name = "project1.module2", externalProjectPath = "$projectPath/module2")
      }
    )
    importData(
      project(name = "project2", projectPath = "$projectPath/project2", systemId = systemId2) {
        module(name = "project2", externalProjectPath = projectPath)
        module(name = "project2.module1", externalProjectPath = "$projectPath/module1")
        module(name = "project2.module2", externalProjectPath = "$projectPath/module2")
      }
    )

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