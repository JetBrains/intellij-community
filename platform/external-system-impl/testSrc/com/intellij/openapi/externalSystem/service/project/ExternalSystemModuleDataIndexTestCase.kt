// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManagerImpl
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.platform.externalSystem.testFramework.toDataNode
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.Function
import org.junit.jupiter.api.Assertions
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import com.intellij.platform.externalSystem.testFramework.Project as ProjectDataBuilder

@TestApplication
abstract class ExternalSystemModuleDataIndexTestCase {

  val project: Project by projectFixture()
  val projectPath: String get() = project.basePath!!

  fun getModule(moduleName: String): Module {
    val moduleManager = ModuleManager.getInstance(project)
    val module = moduleManager.findModuleByName(moduleName)
    Assertions.assertNotNull(module) { "Module $moduleName not found" }
    return module!!
  }

  fun assertModules(vararg expectedModuleNames: String) {
    ModuleAssertions.assertModules(project, *expectedModuleNames)
  }

  fun assertModuleNode(
    expectedSystemId: ProjectSystemId,
    expectedModuleName: String,
    expectedProjectPath: String,
    actualModuleNode: DataNode<out ModuleData>?,
  ) {
    Assertions.assertNotNull(actualModuleNode) { "Module node $expectedModuleName not found" }
    val moduleData = actualModuleNode!!.data
    Assertions.assertEquals(expectedSystemId, moduleData.owner)
    Assertions.assertEquals(expectedModuleName, moduleData.internalName)
    Assertions.assertEquals(expectedProjectPath, moduleData.linkedExternalProjectPath)
  }

  fun createManager(systemId: ProjectSystemId, vararg projectPaths: String): ExternalSystemManager<*, *, *, *, *> {
    val projectSettings = projectPaths.map { projectPath ->
      mock<ExternalProjectSettings>().also {
        whenever(it.externalProjectPath).thenReturn(projectPath)
      }
    }
    val settings = mock<AbstractExternalSystemSettings<*, *, *>>().also {
      whenever(it.linkedProjectsSettings).thenReturn(projectSettings)
    }
    val localSettings = mock<AbstractExternalSystemLocalSettings<*>>()
    return mock<ExternalSystemManager<*, *, *, *, *>>().also {
      whenever(it.systemId).thenReturn(systemId)
      whenever(it.settingsProvider).thenReturn(Function { settings })
      whenever(it.localSettingsProvider).thenReturn(Function { localSettings })
    }
  }

  fun importData(projectDataBuilder: ProjectDataBuilder) {
    val projectNode = projectDataBuilder.toDataNode()
    val systemId = projectNode.data.owner
    val projectPath = projectNode.data.linkedExternalProjectPath
    val projectInfo = InternalExternalProjectInfo(systemId, projectPath, projectNode)
    ProjectDataManagerImpl.getInstance().updateExternalProjectData(project, projectInfo)
    ProjectDataManager.getInstance().importData(projectNode, project)
  }
}