// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.importing.AbstractOpenProjectProvider
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestCase
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.testFramework.openProjectAsync
import com.intellij.util.io.systemIndependentPath
import java.util.*
import javax.swing.Icon

abstract class AutoLinkTestCase : ExternalSystemTestCase() {
  lateinit var testDisposable: Disposable
    private set

  final override fun runInDispatchThread() = false

  override fun setUp() {
    super.setUp()
    testDisposable = Disposer.newDisposable()
  }

  override fun tearDown() {
    try {
      Disposer.dispose(testDisposable)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  override fun getTestsTempDir() = "tmp${System.currentTimeMillis()}"

  override fun getExternalSystemConfigFileName() = throw UnsupportedOperationException()

  fun createUnlinkedProjectAware(systemId: String, buildFileExtension: String): MockUnlinkedProjectAware {
    return MockUnlinkedProjectAware(ProjectSystemId(systemId), buildFileExtension)
  }

  private fun createProjectOpenProcessor(
    unlinedProjectAware: MockUnlinkedProjectAware,
    isExternalSystem: Boolean = true
  ): ProjectOpenProcessor {
    val openProvider = object : AbstractOpenProjectProvider() {
      override val systemId = unlinedProjectAware.systemId

      override fun isProjectFile(file: VirtualFile): Boolean =
        unlinedProjectAware.isBuildFile(file)

      override fun linkToExistingProject(projectFile: VirtualFile, project: Project) {
        val projectDirectory = getProjectDirectory(projectFile).toNioPath()
        unlinedProjectAware.linkAndLoadProject(project, projectDirectory.systemIndependentPath)
      }
    }
    return object : ProjectOpenProcessor() {
      override val name: String
        get() = unlinedProjectAware.systemId.readableName
      override val icon: Icon?
        get() = null

      override fun canOpenProject(file: VirtualFile): Boolean =
        openProvider.canOpenProject(file)

      override fun doOpenProject(virtualFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
        throw UnsupportedOperationException("openProjectAsync must be used")
      }

      override suspend fun openProjectAsync(virtualFile: VirtualFile,
                                            projectToClose: Project?,
                                            forceOpenInNewFrame: Boolean): Optional<Project> {
        val project = openProvider.openProject(virtualFile, projectToClose, forceOpenInNewFrame)
        if (project != null && !isExternalSystem) {
          project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, null)
          project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, null)
        }
        return Optional.ofNullable(project)
      }

      override fun canImportProjectAfterwards(): Boolean = true
      override fun importProjectAfterwards(project: Project, file: VirtualFile) =
        openProvider.linkToExistingProject(file, project)
    }
  }

  fun createAndRegisterUnlinkedProjectAware(systemId: String, buildFileExtension: String): MockUnlinkedProjectAware {
    val unlinedProjectAware = createUnlinkedProjectAware(systemId, buildFileExtension)
    ExternalSystemUnlinkedProjectAware.EP_NAME.point.registerExtension(unlinedProjectAware, testDisposable)
    return unlinedProjectAware
  }

  fun createAndRegisterProjectOpenProcessor(
    unlinedProjectAware: MockUnlinkedProjectAware,
    isExternalSystem: Boolean = true
  ): ProjectOpenProcessor {
    val projectOpenProcessor = createProjectOpenProcessor(unlinedProjectAware, isExternalSystem)
    ProjectOpenProcessor.EXTENSION_POINT_NAME.point.registerExtension(projectOpenProcessor, testDisposable)
    return projectOpenProcessor
  }

  fun createDummyCompilerXml(relativePath: String) {
    createProjectSubFile(relativePath, """
      <?xml version="1.0" encoding="UTF-8"?>
      <project version="4">
        <component name="CompilerConfiguration">
          <bytecodeTargetLevel target="14" />
        </component>
      </project>
    """.trimIndent())
  }

  fun createDummyModulesXml(relativePath: String) {
    createProjectSubFile(relativePath, """
      <?xml version="1.0" encoding="UTF-8"?>
      <project version="4">
        <component name="ProjectModuleManager">
          <modules>
            <module fileurl="file://${'$'}PROJECT_DIR${'$'}/project.iml" filepath="${'$'}PROJECT_DIR${'$'}/project.iml" />
          </modules>
        </component>
      </project>
    """.trimIndent())
  }

  suspend fun openProjectAsync(virtualFile: VirtualFile): Project {
    return openProjectAsync(virtualFile, UnlinkedProjectStartupActivity())
  }

  fun assertNotificationAware(project: Project, vararg projects: ExternalSystemProjectId) {
    val message = when (projects.isEmpty()) {
      true -> "Notification must be expired"
      else -> "Notification must be notified"
    }
    val notificationAware = UnlinkedProjectNotificationAware.getInstance(project)
    assertEquals(message, projects.toSet(), notificationAware.getProjectsWithNotification())
  }

  fun assertLinkedProjects(unlinedProjectAware: MockUnlinkedProjectAware, linkedProjects: Int) {
    assertEquals(linkedProjects, unlinedProjectAware.linkCounter.get())
  }
}