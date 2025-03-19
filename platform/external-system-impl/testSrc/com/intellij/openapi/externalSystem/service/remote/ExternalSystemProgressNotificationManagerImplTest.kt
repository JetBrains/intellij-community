// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.remote

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.internal.AbstractExternalSystemTask
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl.Companion.assertListenersReleased
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl.Companion.getListeners
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.util.ThrowableRunnable
import org.assertj.core.api.Assertions.assertThat

class ExternalSystemProgressNotificationManagerImplTest : UsefulTestCase() {

  lateinit var testFixture: IdeaProjectTestFixture
  lateinit var project: Project
  lateinit var notificationManager: ExternalSystemProgressNotificationManager

  override fun setUp() {
    super.setUp()

    testFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name).fixture
    testFixture.setUp()
    project = testFixture.project
    notificationManager = ExternalSystemProgressNotificationManager.getInstance()
  }

  override fun tearDown() {
    RunAll(
      ThrowableRunnable { testFixture.tearDown() },
      ThrowableRunnable { super.tearDown() }
    ).run()
  }

  fun `test listener cleanup`() {
    val existingListeners = getListeners()
    val disposable = Disposer.newDisposable(testRootDisposable, "test task listener cleanup")
    notificationManager.addNotificationListener(DummyTaskNotificationListener(), disposable)

    val task1 = MyTestTask(project) { it.cancel(DummyTaskNotificationListener()) }
    val task2 = MyTestTask(project)
    val taskListener = DummyTaskNotificationListener()
    notificationManager.addNotificationListener(task1.id, taskListener)
    notificationManager.addNotificationListener(task2.id, taskListener)

    assertThat(getListeners()[task1.id]).containsExactly(taskListener)
    assertThat(getListeners()[task2.id]).containsExactly(taskListener)

    task1.execute(DummyTaskNotificationListener())
    task1.cancel(DummyTaskNotificationListener())
    assertListenersReleased(task1.id)

    assertThat(getListeners()[task1.id]).isNull()
    assertThat(getListeners()[task2.id]).containsExactly(taskListener)
    notificationManager.removeNotificationListener(taskListener)

    assertEquals("start ${task1.id};end ${task1.id};", taskListener.logger.toString())

    Disposer.dispose(disposable)
    assertListenersReleased(existingListeners)
  }

  private class DummyTaskNotificationListener : ExternalSystemTaskNotificationListener {
    val logger = java.lang.StringBuilder()
    override fun onStart(projectPath: String, id: ExternalSystemTaskId) {
      logger.append("start $id;")
    }

    override fun onEnd(projectPath: String, id: ExternalSystemTaskId) {
      logger.append("end $id;")
    }
  }
  private class MyTestTask(project: Project, private val actionBeforeTaskFinish: (MyTestTask) -> Unit = {}) :
    AbstractExternalSystemTask(ProjectSystemId.IDE, ExternalSystemTaskType.EXECUTE_TASK, project, "") {
    override fun doCancel(): Boolean = true

    override fun doExecute() {
      actionBeforeTaskFinish.invoke(this@MyTestTask)
    }
  }
}