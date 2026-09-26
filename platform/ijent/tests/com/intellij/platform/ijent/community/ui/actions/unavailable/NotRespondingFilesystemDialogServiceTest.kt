// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.ui.actions.unavailable

import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.ijent.IjentCallerContext
import com.intellij.platform.ijent.IjentCallerContextElement
import com.intellij.platform.ijent.community.impl.nio.IjentUnavailableUserDecisionException
import com.intellij.platform.ijent.community.impl.nio.ReconnectUiHandleImpl
import com.intellij.platform.ijent.community.ui.actions.unavailable.IjentUnavailableDialogHandler.DialogParams
import com.intellij.platform.ijent.community.ui.actions.unavailable.IjentUnavailableDialogHandler.DialogParams.ProjectIjent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@TestApplication
@Timeout(30)
internal class NotRespondingFilesystemDialogServiceTest {
  private companion object {
    val firstProject = projectFixture()
    val secondProject = projectFixture()
  }

  private val service = NotRespondingFilesystemDialogService()
  private val descriptor = object : EelDescriptor {
    override val name: String = "Test target"
    override val osFamily: EelOsFamily = EelOsFamily.Posix
  }
  private val callerContext = Dispatchers.Default + IjentCallerContextElement(
    IjentCallerContext(isRead = false, isWrite = false, isDispatchThread = false, reconnectUi = ReconnectUiHandleImpl())
  )

  @Test
  fun `calls for the same projects share the running task`(): Unit = timeoutRunBlocking(context = callerContext) {
    val projects = listOf(firstProject.get(), secondProject.get())
    val finish = CompletableDeferred<Unit>()
    val firstContext = CompletableDeferred<Deferred<*>>()
    val secondContext = CompletableDeferred<Deferred<*>>()

    val first = runTask(ProjectIjent(descriptor, projects), { firstContext.complete(it) }) {
      finish.await()
      throw IjentUnavailableUserDecisionException("Close the projects")
    }
    val second = runTask(ProjectIjent(descriptor, projects.reversed()), { secondContext.complete(it) }) {
      error("The second task must not run")
    }
    assertSame(firstContext.await(), secondContext.await())
    assertFalse(second.isCompleted)

    finish.complete(Unit)
    assertEquals("Close the projects", first.await().message)
    assertEquals("Close the projects", second.await().message)
  }

  @Test
  fun `a waiting call runs its task after the first call is cancelled`(): Unit = timeoutRunBlocking(context = callerContext) {
    val params = ProjectIjent(descriptor, listOf(firstProject.get()))
    val first = runTask(params) {
      awaitCancellation()
    }
    val second = runTask(params) {
      throw IjentUnavailableUserDecisionException("The second task ran")
    }
    assertFalse(second.isCompleted)
    first.cancelAndJoin()

    assertEquals("The second task ran", second.await().message)
  }

  @Test
  fun `later calls for the same projects receive the stored decision`(): Unit = timeoutRunBlocking(context = callerContext) {
    val params = ProjectIjent(descriptor, listOf(firstProject.get()))
    val decision = runTask(params) {
      throw IjentUnavailableUserDecisionException("Close the project")
    }.await()

    repeat(2) {
      val repeatedDecision = runTask(params) {
        error("The next task must not run")
      }.await()
      assertEquals(decision.message, repeatedDecision.message)
    }
  }

  @Test
  fun `a new project gets a new task after cancellation`(): Unit = timeoutRunBlocking(context = callerContext) {
    val first = runTask(ProjectIjent(descriptor, listOf(firstProject.get()))) {
      awaitCancellation()
    }
    first.cancelAndJoin()

    val second = runTask(ProjectIjent(descriptor, listOf(secondProject.get()))) {
      throw IjentUnavailableUserDecisionException("Close the second project")
    }
    assertEquals("Close the second project", second.await().message)
  }

  @Test
  fun `a new project gets a new task after a decision`(): Unit = timeoutRunBlocking(context = callerContext) {
    val first = runTask(ProjectIjent(descriptor, listOf(firstProject.get()))) {
      throw IjentUnavailableUserDecisionException("Close the first project")
    }
    assertEquals("Close the first project", first.await().message)

    val second = runTask(ProjectIjent(descriptor, listOf(secondProject.get()))) {
      throw IjentUnavailableUserDecisionException("Close the second project")
    }
    assertEquals("Close the second project", second.await().message)
  }

  @Test
  fun `the decision survives cancellation during child cleanup`(): Unit = timeoutRunBlocking(context = callerContext) {
    val params = ProjectIjent(descriptor, listOf(firstProject.get()))
    val cleanupStarted = CompletableDeferred<Unit>()
    val finishCleanup = CompletableDeferred<Unit>()
    val first = runTask(params) {
      coroutineScope {
        launch(start = CoroutineStart.UNDISPATCHED) {
          try {
            awaitCancellation()
          }
          finally {
            withContext(NonCancellable) {
              cleanupStarted.complete(Unit)
              finishCleanup.await()
            }
          }
        }
        throw IjentUnavailableUserDecisionException("Close the project")
      }
    }
    cleanupStarted.await()
    first.cancel()
    finishCleanup.complete(Unit)
    first.join()

    val decision = runTask(params) {
      error("The next task must not run")
    }.await()
    assertEquals("Close the project", decision.message)
  }

  private suspend fun CoroutineScope.runTask(
    dialogParams: DialogParams,
    onComputing: (Deferred<*>) -> Unit = {},
    task: suspend () -> Nothing,
  ): Deferred<IjentUnavailableUserDecisionException> {
    val registered = CompletableDeferred<Unit>()
    val result = async {
      assertFalse(ApplicationManager.getApplication().isDispatchThread)
      try {
        expectDecision {
          service.doOnceOrWait(dialogParams, {
            onComputing(it)
            registered.complete(Unit)
          }) {
            assertTrue(ApplicationManager.getApplication().isDispatchThread)
            task()
          }
        }
      }
      finally {
        registered.complete(Unit)
      }
    }
    registered.await()
    return result
  }

  private suspend fun expectDecision(task: suspend () -> Nothing): IjentUnavailableUserDecisionException {
    try {
      task()
    }
    catch (e: IjentUnavailableUserDecisionException) {
      return e
    }
  }
}
