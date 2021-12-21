// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.testFramework

import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestUtil.TEST_EXTERNAL_SYSTEM_ID
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import java.io.File

abstract class ExternalSystemProjectTestCase : HeavyPlatformTestCase() {
  protected val projectPath by lazy { project.basePath!! }

  override fun setUp() {
    super.setUp()
    ExtensionTestUtil.addExtensions(ExternalSystemManager.EP_NAME, listOf(TestExternalSystemManager(project)), testRootDisposable)
  }

  override fun setUpModule() {
    // do not create mainModule
  }

  override fun isCreateDirectoryBasedProject() = true

  fun project(name: String = "project",
              projectPath: String = this.projectPath,
              systemId: ProjectSystemId = TEST_EXTERNAL_SYSTEM_ID,
              init: Project.() -> Unit): Project {
    return com.intellij.platform.externalSystem.testFramework.project(name, projectPath, systemId, init)
  }

  protected fun applyProjectModel(vararg projectModels: Project) {
    applyProjectModel(projectModels.map { it.toDataNode() })
  }

  private fun applyProjectModel(nodes: List<DataNode<ProjectData>>) {
    val projectManager = ExternalProjectsManagerImpl.getInstance(project)
    for (node in nodes) {
      val projectSystemId = node.data.owner
      val settings = ExternalSystemApiUtil.getSettings(project, projectSystemId)
      val projectPath = node.data.linkedExternalProjectPath
      val isLinked = settings.linkedProjectsSettings.map { it.externalProjectPath }.contains(projectPath)
      if (!isLinked) {
        settings.linkProject(createProjectSettings().also { it.externalProjectPath = projectPath })
      }
      val externalModulePaths = ExternalSystemApiUtil.findAll(node, ProjectKeys.MODULE).map { it.data.linkedExternalProjectPath }.toSet()
      settings.getLinkedProjectSettings(projectPath)!!.setModules(externalModulePaths)

      ProjectDataManager.getInstance().importData(node, project, true)
      val projectInfo = InternalExternalProjectInfo(projectSystemId, projectPath, node)
      projectManager.updateExternalProjectData(projectInfo)
    }
  }

  protected open fun createProjectSettings(): ExternalProjectSettings = TestExternalProjectSettings()

  protected fun createProjectSubDirectory(relativePath: String) {
    val file = File(projectPath, relativePath)
    FileUtil.ensureExists(file)
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
  }
}