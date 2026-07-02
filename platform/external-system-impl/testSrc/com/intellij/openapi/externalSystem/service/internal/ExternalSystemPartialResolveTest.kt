// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.internal

import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.importing.ProjectResolverPolicy
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.EXECUTE_TASK
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT
import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemTaskConfigurationType
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager
import com.intellij.openapi.externalSystem.testFramework.fixtures.multiProjectFixture
import com.intellij.openapi.externalSystem.testFramework.fixtures.projectFixture
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestUtil.TEST_EXTERNAL_SYSTEM_ID
import com.intellij.platform.externalSystem.testFramework.TestExternalProjectSettings
import com.intellij.platform.externalSystem.testFramework.TestExternalSystemExecutionSettings
import com.intellij.platform.externalSystem.testFramework.TestExternalSystemManager
import com.intellij.platform.externalSystem.testFramework.linkProject
import com.intellij.platform.externalSystem.testFramework.project
import com.intellij.platform.externalSystem.testFramework.toDataNode
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEmpty
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertSingle
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.common.mock.requireImplemented
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
@SystemProperty("force.execute.activated.tasks", true.toString())
internal class ExternalSystemPartialResolveTest {

  private val projectRootFixture = tempPathFixture()
  private val projectRoot by projectRootFixture

  private val projectsFixture = multiProjectFixture()

  private val projectFixture = projectsFixture.projectFixture(projectRootFixture)
  private val project by projectFixture

  private val recordingManager by testFixture {
    val project = projectFixture.init()
    val manager = RecordingExternalSystemManager(project).apply {
      addSyncTask(projectRoot, BEFORE_SYNC_TASK, ExternalSystemTaskActivator.Phase.BEFORE_SYNC)
      addSyncTask(projectRoot, AFTER_SYNC_TASK, ExternalSystemTaskActivator.Phase.AFTER_SYNC)
    }
    initialized(manager) {}
  }

  private val recordingDataService by testFixture {
    initialized(RecordingProjectDataService()) {}
  }

  private val recordingCallback by testFixture {
    initialized(RecordingRefreshCallback()) {}
  }

  private val recordingListener by testFixture {
    val listener = RecordingTaskNotificationListener()
    val manager = ExternalSystemProgressNotificationManager.getInstance()
    manager.addNotificationListener(listener)
    initialized(listener) {
      manager.removeNotificationListener(listener)
    }
  }

  private val projectSettings by testFixture {
    val projectRoot = projectRootFixture.init()
    val projectSettings = TestExternalProjectSettings().apply {
      externalProjectPath = projectRoot.toCanonicalPath()
    }
    initialized(projectSettings) {}
  }

  private val projectData by testFixture {
    val projectRoot = projectRootFixture.init()
    val projectData = project(PROJECT_NAME, projectRoot.toCanonicalPath()) {
      module(MODULE_NAME, projectRoot.resolve(MODULE_NAME).toCanonicalPath())
    }
    initialized(projectData.toDataNode()) {}
  }

  private val projectPath get() = projectRoot.toCanonicalPath()
  private val modulePath get() = projectRoot.resolve(MODULE_NAME).toCanonicalPath()

  @BeforeEach
  fun setUp(@TestDisposable disposable: Disposable) {
    setRegistry(TEST_EXTERNAL_SYSTEM_ID.id + ExternalSystemConstants.USE_IN_PROCESS_COMMUNICATION_REGISTRY_KEY_SUFFIX, true, disposable)
    ExternalSystemManager.EP_NAME.point.registerExtension(recordingManager, disposable)
    ProjectDataService.EP_NAME.point.registerExtension(recordingDataService, disposable)
    ConfigurationType.CONFIGURATION_TYPE_EP.point.registerExtension(TestExternalSystemConfigurationType(), disposable)
  }

  @Test
  fun `test resolve without issues`(): Unit = runBlocking {

    recordingManager.setResolveProjectInfo { projectData }

    linkProject(project, TEST_EXTERNAL_SYSTEM_ID, projectSettings) {
      withCallback(recordingCallback)
    }

    val actualProjectData = ExternalSystemApiUtil.findProjectInfo(project, TEST_EXTERNAL_SYSTEM_ID, projectPath)
    assertNotNull(actualProjectData)
    assertEquals(projectData, actualProjectData.externalProjectStructure)
    assertNotEquals(NO_TIMESTAMP, actualProjectData.lastImportTimestamp)
    assertNotEquals(NO_TIMESTAMP, actualProjectData.lastSuccessfulImportTimestamp)
    assertSingle(modulePath, projectSettings.modules)
    recordingDataService.assertCounters(numImports = 1)
    recordingManager.assertCounters(numBeforeSync = 1, numAfterSync = 1)
    recordingCallback.assertCounters(numSuccesses = 1, numFailures = 0)
    recordingListener.assertCounters(RESOLVE_PROJECT, numSuccesses = 1, numFailures = 0)
    recordingListener.assertCounters(EXECUTE_TASK, numSuccesses = 2, numFailures = 0)
  }

  @Test
  fun `test resolve with exception`(): Unit = runBlocking {

    recordingManager.setResolveProjectInfo { throw ExternalSystemException() }

    linkProject(project, TEST_EXTERNAL_SYSTEM_ID, projectSettings) {
      withCallback(recordingCallback)
    }

    val actualProjectData = ExternalSystemApiUtil.findProjectInfo(project, TEST_EXTERNAL_SYSTEM_ID, projectPath)
    assertNotNull(actualProjectData)
    assertNull(actualProjectData.externalProjectStructure)
    assertNotEquals(NO_TIMESTAMP, actualProjectData.lastImportTimestamp)
    assertEquals(NO_TIMESTAMP, actualProjectData.lastSuccessfulImportTimestamp)
    assertEmpty(projectSettings.modules)
    recordingDataService.assertCounters(numImports = 0)
    recordingManager.assertCounters(numBeforeSync = 1, numAfterSync = 0)
    recordingCallback.assertCounters(numSuccesses = 0, numFailures = 1)
    recordingListener.assertCounters(RESOLVE_PROJECT, numSuccesses = 0, numFailures = 1)
    recordingListener.assertCounters(EXECUTE_TASK, numSuccesses = 1, numFailures = 0)
  }

  @Test
  fun `test resolve with partial data policy`(): Unit = runBlocking {

    recordingManager.setResolveProjectInfo { projectData }

    linkProject(project, TEST_EXTERNAL_SYSTEM_ID, projectSettings) {
      withCallback(recordingCallback)
      projectResolverPolicy(ProjectResolverPolicy { true })
    }

    val actualProjectData = ExternalSystemApiUtil.findProjectInfo(project, TEST_EXTERNAL_SYSTEM_ID, projectPath)
    assertNull(actualProjectData)
    assertEmpty(projectSettings.modules)
    recordingDataService.assertCounters(numImports = 0)
    recordingManager.assertCounters(numBeforeSync = 1, numAfterSync = 1)
    recordingCallback.assertCounters(numSuccesses = 1, numFailures = 0)
    recordingListener.assertCounters(RESOLVE_PROJECT, numSuccesses = 1, numFailures = 0)
    recordingListener.assertCounters(EXECUTE_TASK, numSuccesses = 2, numFailures = 0)
  }

  @Test
  fun `test resolve with partial resolve exception`(): Unit = runBlocking {

    recordingManager.setResolveProjectInfo { throw ExternalSystemPartialResolutionException(projectData) }

    linkProject(project, TEST_EXTERNAL_SYSTEM_ID, projectSettings) {
      withCallback(recordingCallback)
    }

    val actualProjectData = ExternalSystemApiUtil.findProjectInfo(project, TEST_EXTERNAL_SYSTEM_ID, projectPath)
    assertNotNull(actualProjectData)
    assertEquals(projectData, actualProjectData.externalProjectStructure)
    assertNotEquals(NO_TIMESTAMP, actualProjectData.lastImportTimestamp)
    assertEquals(NO_TIMESTAMP, actualProjectData.lastSuccessfulImportTimestamp)
    assertSingle(modulePath, projectSettings.modules)
    recordingDataService.assertCounters(numImports = 1)
    recordingManager.assertCounters(numBeforeSync = 1, numAfterSync = 0)
    recordingCallback.assertCounters(numSuccesses = 0, numFailures = 1)
    recordingListener.assertCounters(RESOLVE_PROJECT, numSuccesses = 0, numFailures = 1)
    recordingListener.assertCounters(EXECUTE_TASK, numSuccesses = 1, numFailures = 0)

  }

  class RecordingExternalSystemManager(private val project: Project) : TestExternalSystemManager(project) {

    private var resolveProjectInfo: (() -> DataNode<ProjectData>)? = null

    private val beforeSyncTasks = AtomicInteger(0)
    private val afterSyncTasks = AtomicInteger(0)

    fun setResolveProjectInfo(task: () -> DataNode<ProjectData>) {
      resolveProjectInfo = task
    }

    fun assertCounters(numBeforeSync: Int, numAfterSync: Int) {
      assertEquals(numBeforeSync, beforeSyncTasks.get())
      assertEquals(numAfterSync, afterSyncTasks.get())
    }

    fun addSyncTask(projectRoot: Path, taskName: String, phase: ExternalSystemTaskActivator.Phase) {
      ExternalProjectsManagerImpl.getInstance(project).taskActivator.addTask(
        ExternalSystemTaskActivator.TaskActivationEntry(TEST_EXTERNAL_SYSTEM_ID, phase, projectRoot.toCanonicalPath(), taskName)
      )
    }

    override fun getProjectResolverClass(): Class<out ExternalSystemProjectResolver<TestExternalSystemExecutionSettings>> =
      RecordingExternalSystemProjectResolver::class.java

    override fun getTaskManagerClass(): Class<out ExternalSystemTaskManager<TestExternalSystemExecutionSettings>> =
      RecordingExternalSystemTaskManager::class.java

    class RecordingExternalSystemProjectResolver : ExternalSystemProjectResolver<TestExternalSystemExecutionSettings> {

      // @formatter:off
      override fun resolveProjectInfo(id: ExternalSystemTaskId, projectPath: String, isPreviewMode: Boolean, settings: TestExternalSystemExecutionSettings?, resolverPolicy: ProjectResolverPolicy?, listener: ExternalSystemTaskNotificationListener): DataNode<ProjectData> {
        // @formatter:on
        val manager = ExternalSystemApiUtil.getManager(id.projectSystemId) as RecordingExternalSystemManager
        return manager.requireImplemented(RecordingExternalSystemManager::resolveProjectInfo)()
      }

      override fun cancelTask(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean {
        return false
      }
    }

    class RecordingExternalSystemTaskManager : ExternalSystemTaskManager<TestExternalSystemExecutionSettings> {

      // @formatter:off
      override fun executeTasks(projectPath: String, id: ExternalSystemTaskId, settings: TestExternalSystemExecutionSettings, listener: ExternalSystemTaskNotificationListener) {
        // @formatter:on
        val manager = ExternalSystemApiUtil.getManager(id.projectSystemId) as RecordingExternalSystemManager
        if (BEFORE_SYNC_TASK in settings.tasks) {
          manager.beforeSyncTasks.incrementAndGet()
        }
        if (AFTER_SYNC_TASK in settings.tasks) {
          manager.afterSyncTasks.incrementAndGet()
        }
      }

      override fun cancelTask(id: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean {
        return false
      }
    }
  }

  private class RecordingProjectDataService : ProjectDataService<ProjectData, Any> {

    private val importCounter = AtomicInteger(0)

    fun assertCounters(numImports: Int) {
      assertEquals(numImports, importCounter.get())
    }

    override fun getTargetDataKey() = ProjectKeys.PROJECT

    // @formatter:off
    override fun importData(toImport: Collection<DataNode<ProjectData>>, projectData: ProjectData?, project: Project, modelsProvider: IdeModifiableModelsProvider) {
      // @formatter:on
      importCounter.addAndGet(toImport.size)
    }

    // @formatter:off
    override fun computeOrphanData(toImport: Collection<DataNode<ProjectData>>, projectData: ProjectData, project: Project, modelsProvider: IdeModifiableModelsProvider): Computable<Collection<Any>> {
      // @formatter:on
      return Computable { emptyList() }
    }

    // @formatter:off
    override fun removeData(toRemove: Computable<out Collection<Any>>, toIgnore: Collection<DataNode<ProjectData>>, projectData: ProjectData, project: Project, modelsProvider: IdeModifiableModelsProvider) {
      // @formatter:on
    }
  }

  private class RecordingRefreshCallback : ExternalProjectRefreshCallback {

    private val successCounter = AtomicInteger(0)
    private val failureCounter = AtomicInteger(0)

    fun assertCounters(numSuccesses: Int, numFailures: Int) {
      assertEquals(numSuccesses, successCounter.get())
      assertEquals(numFailures, failureCounter.get())
    }

    override fun onSuccess(externalProject: DataNode<ProjectData>?) {
      successCounter.incrementAndGet()
    }

    override fun onFailure(errorMessage: String, errorDetails: String?) {
      failureCounter.incrementAndGet()
    }
  }

  private class RecordingTaskNotificationListener : ExternalSystemTaskNotificationListener {

    private val counters = ConcurrentHashMap<ExternalSystemTaskType, TaskNotificationCounter>()

    private fun counter(type: ExternalSystemTaskType): TaskNotificationCounter {
      return counters.computeIfAbsent(type) { TaskNotificationCounter() }
    }

    fun assertCounters(type: ExternalSystemTaskType, numSuccesses: Int, numFailures: Int) {
      counter(type).assertCounters(numSuccesses, numFailures)
    }

    override fun onSuccess(projectPath: String, id: ExternalSystemTaskId) {
      counter(id.type).onSuccess()
    }

    override fun onFailure(projectPath: String, id: ExternalSystemTaskId, exception: Exception) {
      counter(id.type).onFailure()
    }

    private class TaskNotificationCounter {

      private val successCounter = AtomicInteger(0)
      private val failureCounter = AtomicInteger(0)

      fun assertCounters(numSuccesses: Int, numFailures: Int) {
        assertEquals(numSuccesses, successCounter.get())
        assertEquals(numFailures, failureCounter.get())
      }

      fun onSuccess() {
        successCounter.incrementAndGet()
      }

      fun onFailure() {
        failureCounter.incrementAndGet()
      }
    }
  }

  private class TestExternalSystemConfigurationType : AbstractExternalSystemTaskConfigurationType(TEST_EXTERNAL_SYSTEM_ID) {
    override fun getConfigurationFactoryId(): String = "Test_external_system_id"
  }

  companion object {
    private const val PROJECT_NAME = "project"
    private const val MODULE_NAME = "module"
    private const val BEFORE_SYNC_TASK = "beforeSyncTask"
    private const val AFTER_SYNC_TASK = "afterSyncTask"
    private const val NO_TIMESTAMP = -1L

    private fun setRegistry(name: String, value: Boolean, disposable: Disposable) {
      val registryValue = Registry.get(name)
      registryValue.setValue(value)
      disposable.whenDisposed { registryValue.resetToDefault() }
    }

    private fun assertNotEquals(unexpected: Any?, actual: Any?) {
      assertThat(actual).isNotEqualTo(unexpected)
    }
  }
}
