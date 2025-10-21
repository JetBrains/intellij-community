// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskState
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.internal.AbstractExternalSystemTask
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager
import com.intellij.openapi.util.Disposer
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestCase
import com.intellij.testFramework.RunAll.Companion.runAll
import org.junit.jupiter.api.Assertions

abstract class AbstractExternalSystemTaskTestCase : ExternalSystemTestCase() {

  private lateinit var testDisposable: Disposable

  override fun getTestsTempDir(): String = "tmp"
  override fun getExternalSystemConfigFileName(): String? = null

  override fun setUp() {
    super.setUp()

    testDisposable = Disposer.newDisposable("AbstractExternalSystemTaskTestCase#testDisposable")
  }

  override fun tearDown() {
    runAll(
      { if (::testDisposable.isInitialized) Disposer.dispose(testDisposable) },
      { super.tearDown() }
    )
  }

  fun checkExternalSystemTaskState(id: ExternalSystemTaskId, expectedState: ExternalSystemTaskState) {
    val processingManager = ExternalSystemProcessingManager.getInstance()
    val task = processingManager.findTask(id)
    Assertions.assertNotNull(task) {
      "Cannot find executing task for id: $id"
    }
    val actualState = task!!.state
    Assertions.assertEquals(expectedState, actualState) {
      "The state should be $expectedState, but was $actualState"
    }
  }

  fun createExternalSystemTask(
    doExecute: AbstractExternalSystemTask.() -> Unit,
    doCancel: AbstractExternalSystemTask.() -> Boolean = { throw UnsupportedOperationException() },
  ): AbstractExternalSystemTask {
    val systemId = ProjectSystemId.IDE
    val taskType = ExternalSystemTaskType.EXECUTE_TASK
    val projectPath = myProject.basePath!!
    return object : AbstractExternalSystemTask(systemId, taskType, myProject, projectPath) {
      override fun doExecute() = doExecute(this)
      override fun doCancel(): Boolean = doCancel(this)
    }
  }

  fun whenExternalSystemTaskEnded(action: (ExternalSystemTaskId) -> Unit) {
    addNotificationListener(object : ExternalSystemTaskNotificationListener {
      override fun onEnd(projectPath: String, id: ExternalSystemTaskId) {
        action(id)
      }
    })
  }

  fun whenExternalSystemTaskCancelled(action: (ExternalSystemTaskId) -> Unit) {
    addNotificationListener(object : ExternalSystemTaskNotificationListener {
      override fun onCancel(projectPath: String, id: ExternalSystemTaskId) {
        action(id)
      }
    })
  }

  private fun addNotificationListener(listener: ExternalSystemTaskNotificationListener) {
    ExternalSystemTaskNotificationListener.EP_NAME.point.registerExtension(listener, testDisposable)
  }
}