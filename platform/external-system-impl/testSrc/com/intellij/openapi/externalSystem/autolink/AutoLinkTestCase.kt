// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.importing.AbstractOpenProjectProvider
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.getResolvedPath
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.testFramework.StartupActivityTestUtil
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.openProjectAsync
import com.intellij.testFramework.utils.vfs.getDirectory
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import javax.swing.Icon
import kotlin.io.path.invariantSeparatorsPathString

@TestApplication
abstract class AutoLinkTestCase {
  lateinit var testDisposable: Disposable

  private lateinit var fileFixture: TempDirTestFixture

  lateinit var testRoot: VirtualFile

  @BeforeEach
  fun setUp() {
    testDisposable = Disposer.newDisposable()

    fileFixture = IdeaTestFixtureFactory.getFixtureFactory()
      .createTempDirTestFixture()
    fileFixture.setUp()

    runBlocking {
      edtWriteAction {
        testRoot = fileFixture.findOrCreateDir("AutoLinkTestCase")
      }
    }
  }

  @AfterEach
  fun tearDown() {
    runAll(
      { fileFixture.tearDown() },
      { Disposer.dispose(testDisposable) }
    )
  }

  suspend fun openProject(relativePath: String): Project {
    val projectRoot = testRoot.getDirectory(relativePath)
    val project = openProjectAsync(projectRoot, UnlinkedProjectStartupActivity())
    StartupActivityTestUtil.waitForProjectActivitiesToComplete(project)
    return project
  }

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

      override suspend fun linkProject(projectFile: VirtualFile, project: Project) {
        val projectDirectory = getProjectDirectory(projectFile).toNioPath()
        unlinedProjectAware.linkAndLoadProjectAsync(project, projectDirectory.invariantSeparatorsPathString)
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
                                            forceOpenInNewFrame: Boolean): Project? {
        val project = openProvider.openProject(virtualFile, projectToClose, forceOpenInNewFrame)
        if (project != null && !isExternalSystem) {
          project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, null)
          project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, null)
        }
        return project
      }

      override fun canImportProjectAfterwards(): Boolean = true
      override suspend fun importProjectAfterwardsAsync(project: Project, file: VirtualFile) =
        openProvider.linkToExistingProjectAsync(file, project)
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

  suspend fun assertNotificationAware(project: Project, vararg projects: Pair<String, String>) {
    val expectedProjectIds = readAction {
      projects.map { (systemId, relativePath) ->
        val externalProjectPath = testRoot.toNioPath().getResolvedPath(relativePath).toCanonicalPath()
        ExternalSystemProjectId(ProjectSystemId(systemId), externalProjectPath)
      }
    }
    val notificationAware = UnlinkedProjectNotificationAware.getInstance(project)
    Assertions.assertEquals(expectedProjectIds.toSet(), notificationAware.getProjectsWithNotification()) {
      when (projects.isEmpty()) {
        true -> "Notification must be expired"
        else -> "Notification must be notified"
      }
    }
  }

  fun assertLinkedProjects(unlinedProjectAware: MockUnlinkedProjectAware, linkedProjects: Int) {
    Assertions.assertEquals(linkedProjects, unlinedProjectAware.linkCounter.get())
  }
}