// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskState
import com.intellij.platform.testFramework.assertion.listenerAssertion.ListenerAssertion
import com.intellij.openapi.progress.ProcessCanceledException

class AbstractExternalSystemTaskTest : AbstractExternalSystemTaskTestCase() {

  fun `test execute success`() {
    val task = createExternalSystemTask(
      doExecute = {}
    )

    val listenerAssertion = ListenerAssertion()
    whenExternalSystemTaskEnded { id ->
      listenerAssertion.trace {
        checkExternalSystemTaskState(id, ExternalSystemTaskState.FINISHED)
      }
    }

    task.execute()

    listenerAssertion.assertListenerState(1) {
      "The ExternalSystemTaskNotificationListener#onEnd should be called after any execution."
    }
    listenerAssertion.assertListenerFailures()
  }

  fun `test execute cancel`() {
    val task = createExternalSystemTask(
      doExecute = { throw ProcessCanceledException() }
    )

    val listenerAssertion = ListenerAssertion()
    whenExternalSystemTaskEnded { id ->
      listenerAssertion.trace {
        checkExternalSystemTaskState(id, ExternalSystemTaskState.CANCELED)
      }
    }

    task.execute()

    listenerAssertion.assertListenerState(1) {
      "The ExternalSystemTaskNotificationListener#onEnd should be called after any execution."
    }
    listenerAssertion.assertListenerFailures()
  }

  fun `test execute failure`() {
    val task = createExternalSystemTask(
      doExecute = { throw Exception() }
    )

    val listenerAssertion = ListenerAssertion()
    whenExternalSystemTaskEnded { id ->
      listenerAssertion.trace {
        checkExternalSystemTaskState(id, ExternalSystemTaskState.FAILED)
      }
    }

    task.execute()

    listenerAssertion.assertListenerState(1) {
      "The ExternalSystemTaskNotificationListener#onEnd should be called after any execution."
    }
    listenerAssertion.assertListenerFailures()
  }

  fun `test cancel success`() {
    val task = createExternalSystemTask(
      doExecute = { cancel() },
      doCancel = { true }
    )

    val listenerAssertion = ListenerAssertion()
    whenExternalSystemTaskCancelled { id ->
      listenerAssertion.trace {
        checkExternalSystemTaskState(id, ExternalSystemTaskState.CANCELED)
      }
    }

    task.execute()

    listenerAssertion.assertListenerState(1) {
      "The ExternalSystemTaskNotificationListener#onCancel should be called after cancelled execution."
    }
    listenerAssertion.assertListenerFailures()
  }

  fun `test cancel failure`() {
    val task = createExternalSystemTask(
      doExecute = { cancel() },
      doCancel = { throw Exception() }
    )

    val listenerAssertion = ListenerAssertion()
    whenExternalSystemTaskCancelled { id ->
      listenerAssertion.trace {
        checkExternalSystemTaskState(id, ExternalSystemTaskState.CANCELLATION_FAILED)
      }
    }

    task.execute()

    listenerAssertion.assertListenerState(1) {
      "The ExternalSystemTaskNotificationListener#onCancel should be called after cancelled execution."
    }
    listenerAssertion.assertListenerFailures()
  }
}