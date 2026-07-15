// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NavigateUtil")

package com.intellij.platform.ide.navigation

import com.intellij.codeWithMe.ClientId
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.IdeUiService
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.pom.Navigatable
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

private val isNavigationRequestsEnabled: Boolean
  get() = Registry.`is`("ide.navigation.requests")

/**
 * Submits navigation to [navigatable] without waiting for it to finish.
 *
 * When `ide.navigation.requests` is enabled, the navigation is launched asynchronously in [coroutineScope]
 * or, when none is given, in the project navigation service scope.
 * Otherwise, a blocking modal navigation is scheduled on a later EDT event.
 * In both modes the function returns before the navigation completes.
 * UI context is captured immediately for EDT callers and asynchronously for callers on other threads.
 * Whenever possible, prefer [CoroutineScope.requestNavigate].
 *
 * Tests which depend on navigation started outside those fixtures (e.g., via `EditorTestUtil.executeAction`)
 * must explicitly await the pending-navigation barrier (see `NavigationTestUtil.awaitPendingNavigation`)
 * outside a write action.
 * NB: prefer passing a lifecycle-bound [coroutineScope] when possible.
 *
 * @see [CoroutineScope.requestNavigate]
 * @see [NavigationTaskCoordinator.runAfterTasksCompletion]
 *
 * @return [Job] which completes when the navigation task settles: finishes, is canceled
 * by a newer navigation request, or fails. This is an observation handle only
 */
@ApiStatus.Internal
@JvmOverloads
fun requestNavigate(
  project: Project,
  navigatable: Navigatable,
  options: NavigationOptions = NavigationOptions.defaultOptions(),
  dataContext: DataContext? = null,
  coroutineScope: CoroutineScope? = null,
): Job {
  return dispatchNavigateRequest(
    project,
    dataContext,
    coroutineScope,
    navigateAsync = { ctx ->
      project.serviceAsync<NavigationService>().navigate(navigatable, options, ctx)
    }
  ) { ctx ->
    navigateBlocking(project, navigatable, options, ctx)
  }
}

/**
 * Submits navigation to [request] without waiting for it to finish.
 * @see [requestNavigate] for the dispatch and completion semantics.
 */
@ApiStatus.Internal
@JvmOverloads
fun requestNavigate(
  project: Project,
  request: NavigationRequest,
  options: NavigationOptions = NavigationOptions.defaultOptions(),
  dataContext: DataContext? = null,
  coroutineScope: CoroutineScope? = null,
): Job {
  return dispatchNavigateRequest(
    project,
    dataContext,
    coroutineScope,
    navigateAsync = { ctx ->
      project.serviceAsync<NavigationService>().navigate(request, options, ctx)
    }
  ) { ctx ->
    navigateBlocking(project, request, options, ctx)
  }
}

@ApiStatus.Internal
@JvmOverloads
fun requestNavigate(
  project: Project,
  dataContext: DataContext,
  options: NavigationOptions = NavigationOptions.defaultOptions(),
  coroutineScope: CoroutineScope? = null,
): Job {
  return dispatchNavigateRequest(
    project,
    dataContext,
    coroutineScope,
    navigateAsync = { ctx ->
      project.serviceAsync<NavigationService>().navigate(ctx ?: dataContext, options)
    }
  ) { ctx ->
    runWithModalProgressBlocking(project, IdeBundle.message("progress.title.preparing.navigation")) {
      project.serviceAsync<NavigationService>().navigate(ctx ?: dataContext, options)
    }
  }
}

/**
 * Fire-and-forget navigation from a [CoroutineScope].
 * The returned [Job] completes when the navigation task finishes (including cancellation).
 */
@ApiStatus.Internal
fun CoroutineScope.requestNavigate(
  project: Project,
  navigatable: Navigatable,
  options: NavigationOptions = NavigationOptions.defaultOptions(),
  dataContext: DataContext? = null,
): Job {
  return dispatchNavigateRequest(
    project,
    dataContext,
    navigateAsync = { ctx ->
      project.serviceAsync<NavigationService>().navigate(navigatable, options, ctx)
    }
  ) { ctx ->
    navigateBlocking(project, navigatable, options, ctx)
  }
}

/**
 * Fire-and-forget navigation from a [CoroutineScope].
 * @see [CoroutineScope.requestNavigate].
 */
@ApiStatus.Internal
fun CoroutineScope.requestNavigate(
  project: Project,
  request: NavigationRequest,
  options: NavigationOptions = NavigationOptions.defaultOptions(),
  dataContext: DataContext? = null,
): Job {
  return dispatchNavigateRequest(
    project,
    dataContext,
    navigateAsync = { ctx ->
      project.serviceAsync<NavigationService>().navigate(request, options, ctx)
    }
  ) { ctx ->
    navigateBlocking(project, request, options, ctx)
  }
}

private fun dispatchNavigateRequest(
  project: Project,
  dataContext: DataContext?,
  coroutineScope: CoroutineScope?,
  navigateAsync: suspend (DataContext?) -> Unit,
  navigateBlocking: (DataContext?) -> Unit,
): Job {
  if (ApplicationManager.getApplication().isDispatchThread) {
    return createNavigationContext(project, dataContext).dispatchNavigateRequest(
      project,
      coroutineScope,
      navigateAsync,
      navigateBlocking,
    )
  }
  return coroutineScope.dispatchNavigateRequest(project, dataContext, navigateAsync, navigateBlocking)
}

private fun CoroutineScope?.dispatchNavigateRequest(
  project: Project,
  dataContext: DataContext?,
  navigateAsync: suspend (DataContext?) -> Unit,
  navigateBlocking: (DataContext?) -> Unit,
): Job {
  return NavigationTaskCoordinator.getInstance(project).dispatchNavigation(this) {
    val navigateContext = withContext(Dispatchers.EDT) {
      createNavigationContext(project, dataContext)
    }
    withContext(navigateContext.coroutineContext) {
      if (isNavigationRequestsEnabled) {
        navigateAsync(navigateContext.dataContext)
      }
      else {
        withContext(Dispatchers.EDT) {
          navigateBlocking(navigateContext.dataContext)
        }
      }
    }
  }
}

private inline fun NavigationTaskContext.dispatchNavigateRequest(
  project: Project,
  coroutineScope: CoroutineScope?,
  crossinline navigateAsync: suspend (DataContext?) -> Unit,
  crossinline navigateBlocking: (DataContext?) -> Unit,
): Job {
  val coordinator = NavigationTaskCoordinator.getInstance(project)
  return if (isNavigationRequestsEnabled) {
    coordinator.dispatchNavigation(coroutineScope, this) {
      navigateAsync(dataContext)
    }
  }
  else {
    coordinator.dispatchNavigation(coroutineScope, this) {
      withContext(Dispatchers.EDT) {
        navigateBlocking(dataContext)
      }
    }
  }
}

/**
 * Navigates to the specified [navigatable] in a blocking manner, showing a modal progress dialog.
 * This is a blocking version of [NavigationService.navigate].
 */
@RequiresEdt
private fun navigateBlocking(project: Project, navigatable: Navigatable, options: NavigationOptions, dataContext: DataContext?) {
  ThreadingAssertions.assertEventDispatchThread()
  val dataContext = dataContext ?: fetchDataContext(project)
  return runWithModalProgressBlocking(project, IdeBundle.message("progress.title.preparing.navigation")) {
    project.serviceAsync<NavigationService>().navigate(navigatable, options, dataContext)
  }
}

/**
 * Navigates to the specified navigation [request] in a blocking manner, showing a modal progress dialog.
 * This is a blocking version of [NavigationService.navigate].
 */
@RequiresEdt
private fun navigateBlocking(project: Project, request: NavigationRequest, options: NavigationOptions, dataContext: DataContext?) {
  ThreadingAssertions.assertEventDispatchThread()
  val dataContext = dataContext ?: fetchDataContext(project)
  return runWithModalProgressBlocking(project, IdeBundle.message("progress.title.preparing.navigation")) {
    project.serviceAsync<NavigationService>().navigate(request, options, dataContext)
  }
}

/**
 * Captures EDT-only navigation inputs (focus/[DataContext], [ModalityState], [ClientId]).
 * [ClientId] is captured only for context propagation into the async navigation, not for lifetime.
 */
@RequiresEdt
@ApiStatus.Internal
private fun createNavigationContext(project: Project, dataContext: DataContext? = null): NavigationTaskContext {
  ThreadingAssertions.assertEventDispatchThread()
  return NavigationTaskContext(
    getOrCreateAsyncDataContext(project, dataContext),
    modalityState = ModalityState.current(),
    clientIdContext = ClientId.coroutineContext(),
  )
}

@RequiresEdt
private fun getOrCreateAsyncDataContext(project: Project, dataContext: DataContext?): DataContext? {
  ThreadingAssertions.assertEventDispatchThread()
  val context = dataContext ?: fetchDataContext(project) ?: return null
  return IdeUiService.getInstance().createAsyncDataContext(context)
}

@RequiresEdt
private fun fetchDataContext(project: Project): DataContext? {
  ThreadingAssertions.assertEventDispatchThread()
  val component = IdeFocusManager.getInstance(project).getFocusOwner()
  return component?.let { DataManager.getInstance().getDataContext(it) }
}
