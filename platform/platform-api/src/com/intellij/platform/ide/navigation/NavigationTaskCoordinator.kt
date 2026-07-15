// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation

import com.intellij.codeWithMe.ClientId
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * Tracks navigation tasks of a project so that tests can explicitly await their completion
 * instead of relying on `isUnitTestMode` in the production navigation path.
 *
 * Tasks dispatched by [requestNavigate] / [CoroutineScope.requestNavigate] are registered before they start.
 * Direct [NavigationService] calls are tracked from the moment they enter the service implementation.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class NavigationTaskCoordinator(
  private val navigationScope: CoroutineScope,
) {
  private val pendingTasks = ConcurrentCollectionFactory.createConcurrentSet<Job>()

  /**
   * Runs [action] while exposing its execution as pending navigation.
   * A separate token is tracked instead of the caller's job, so completion of [action] completes the tracking entry
   */
  suspend fun <T> runWithTracking(action: suspend () -> T): T {
    val task = Job()
    pendingTasks.add(task)
    task.invokeOnCompletion {
      pendingTasks.remove(task)
    }
    try {
      return action()
    }
    finally {
      task.complete()
    }
  }

  /**
   * Runs [action] on EDT after all currently pending navigation tasks have settled;
   * runs it immediately when nothing is pending.
   * The continuation is tracked as a pending task itself, so tests awaiting pending navigation also wait for it.
   */
  @RequiresEdt
  fun runAfterTasksCompletion(scope: CoroutineScope, action: () -> Unit) {
    ThreadingAssertions.assertEventDispatchThread()
    val barrier = pendingNavigation()
    if (barrier.isCompleted) {
      action()
      return
    }
    val context = ClientId.coroutineContext() + ModalityState.current().asContextElement() + Dispatchers.EDT
    createTask(scope, context) {
      barrier.join()
      action()
    }.start()
  }

  /**
   * The returned [Job] completes when the navigation task finishes (including cancellation).
   */
  fun dispatchNavigation(
    coroutineScope: CoroutineScope? = null,
    navigateContext: NavigationTaskContext,
    action: suspend () -> Unit,
  ): Job {
    val application = ApplicationManager.getApplication()
    val scope = coroutineScope.orServiceScope()
    val task = createTask(scope, navigateContext.coroutineContext, action)
    if (application.isWriteAccessAllowed) {
      application.invokeLater({ task.start() }, navigateContext.modalityState)
    } else {
      task.start()
    }
    return task
  }

  /**
   * Dispatches [action] with [ModalityState.defaultModalityState] on the calling thread.
   * Prefer [dispatchNavigation] with [NavigationTaskContext] when UI context must be captured on the EDT.
   */
  fun dispatchNavigation(coroutineScope: CoroutineScope? = null, action: suspend () -> Unit): Job {
    val application = ApplicationManager.getApplication()
    val modalityState = ModalityState.defaultModalityState()
    val scope = coroutineScope.orServiceScope()
    val context = ClientId.coroutineContext() + modalityState.asContextElement()
    val task = createTask(scope, context, action)
    if (application.isWriteAccessAllowed) {
      application.invokeLater({ task.start() }, modalityState)
    } else {
      task.start()
    }
    return task
  }

  /**
   * Creates a lazy navigation task and atomically registers it,
   * so the task is visible to [pendingNavigation] before the caller starts it.
   */
  private fun createTask(scope: CoroutineScope, context: CoroutineContext, action: suspend () -> Unit): Job {
    val task = scope.launch(context, start = CoroutineStart.LAZY) {
      action()
    }
    pendingTasks.add(task)
    task.invokeOnCompletion {
      pendingTasks.remove(task)
    }
    return task
  }

  /**
   * Builds a snapshot barrier: a job which completes when all navigation tasks pending at this call have completed.
   * Tasks submitted later are not included. Cancellation of a navigation task counts as completion.
   * Failures of navigation tasks are not propagated through it.
   */
  internal fun pendingNavigation(): Job {
    val tasks = pendingTasks.toList()
    val result = Job()
    if (tasks.isEmpty()) {
      result.complete()
      return result
    }

    val remainingTasks = AtomicInteger(tasks.size)
    tasks.forEach { task ->
      task.invokeOnCompletion {
        if (remainingTasks.decrementAndGet() == 0) {
          result.complete()
        }
      }
    }
    return result
  }

  private fun CoroutineScope?.orServiceScope(): CoroutineScope = this ?: navigationScope

  companion object {
    @JvmStatic
    fun getInstance(project: Project): NavigationTaskCoordinator = project.service()
  }
}
