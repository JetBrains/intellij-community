// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.platform.externalSystem.testFramework.TestExternalProjectSettings
import com.intellij.platform.externalSystem.testFramework.TestExternalSystemManager
import com.intellij.platform.externalSystem.testFramework.toDataNode
import com.intellij.project.stateStore
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import kotlin.io.path.Path
import kotlin.io.path.exists

class ExternalSystemProjectSaveTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()

  }

  @JvmField
  @Rule
  val disposableRule = DisposableRule()

  @JvmField
  @Rule
  val projectRule = ProjectRule()

  @Test
  fun `iml file of removed module is also removed`() {
    ExtensionTestUtil.addExtensions(ExternalSystemManager.EP_NAME, listOf(TestExternalSystemManager(projectRule.project)), disposableRule.disposable)

    val model = com.intellij.platform.externalSystem.testFramework.project("MyProject", projectRule.project.basePath!!) {
      module("Module1")
      module("Module2")
    }
    val nodes = model.toDataNode()
    applyProjectModel(listOf(nodes))
    runBlocking { projectRule.project.stateStore.save() }

    val filePath = ModuleManager.getInstance(projectRule.project).findModuleByName("Module1")!!.moduleFilePath
    assertTrue(Path(filePath).exists())

    val model2 = com.intellij.platform.externalSystem.testFramework.project("MyProject", projectRule.project.basePath!!) {
      module("Module2")
    }
    applyProjectModel(listOf(model2.toDataNode()))
    runBlocking { projectRule.project.stateStore.save() }

    assertFalse(Path(filePath).exists())
  }

  private fun applyProjectModel(nodes: List<DataNode<ProjectData>>) {
    val projectManager = ExternalProjectsManagerImpl.getInstance(projectRule.project)
    for (node in nodes) {
      val projectSystemId = node.data.owner
      val settings = ExternalSystemApiUtil.getSettings(projectRule.project, projectSystemId)
      val projectPath = node.data.linkedExternalProjectPath
      val isLinked = settings.linkedProjectsSettings.map { it.externalProjectPath }.contains(projectPath)
      if (!isLinked) {
        settings.linkProject(TestExternalProjectSettings().also { it.externalProjectPath = projectPath })
      }
      val externalModulePaths = ExternalSystemApiUtil.findAll(node, ProjectKeys.MODULE).map { it.data.linkedExternalProjectPath }.toSet()
      settings.getLinkedProjectSettings(projectPath)!!.setModules(externalModulePaths)

      ProjectDataManager.getInstance().importData(node, projectRule.project)
      val projectInfo = InternalExternalProjectInfo(projectSystemId, projectPath, node)
      projectManager.updateExternalProjectData(projectInfo)
    }
  }
}