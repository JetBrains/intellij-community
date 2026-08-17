// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NavigateUtil")

package com.intellij.platform.ide.navigation

import com.intellij.codeWithMe.ClientId
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.IdeUiService
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.OpenFileDescriptor
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
    options,
    navigateAsync = { ctx ->
      project.serviceAsync<NavigationService>().navigate(navigatable, ctx.navigationOptions)
    }
  ) { ctx ->
    navigateBlocking(project, navigatable, ctx.navigationOptions)
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
    options,
    navigateAsync = { ctx ->
      project.serviceAsync<NavigationService>().navigate(request, ctx.navigationOptions)
    }
  ) { ctx ->
    navigateBlocking(project, request, ctx.navigationOptions)
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
    options,
    navigateAsync = { ctx ->
      navigate(project, ctx.dataContext, ctx.navigationOptions)
    }
  ) { ctx ->
    runWithModalProgressBlocking(project, IdeBundle.message("progress.title.preparing.navigation")) {
      navigate(project, ctx.dataContext, ctx.navigationOptions)
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
    options,
    navigateAsync = { ctx ->
      project.serviceAsync<NavigationService>().navigate(navigatable, ctx.navigationOptions)
    }
  ) { ctx ->
    navigateBlocking(project, navigatable, ctx.navigationOptions)
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
    options,
    navigateAsync = { ctx ->
      project.serviceAsync<NavigationService>().navigate(request, ctx.navigationOptions)
    }
  ) { ctx ->
    navigateBlocking(project, request, ctx.navigationOptions)
  }
}

/**
 * The targets are resolved from async [dataContext] inside the navigation task, so a navigation which is still resolving them
 * is canceled by a newer one instead of outliving it.
 */
internal suspend fun navigate(project: Project, dataContext: DataContext?, options: NavigationOptions): Boolean {
  return project.serviceAsync<NavigationService>().navigate(options) {
    readAction {
      dataContext?.getData(CommonDataKeys.NAVIGATABLE_ARRAY)?.toList()
    }.orEmpty()
  }
}

private fun dispatchNavigateRequest(
  project: Project,
  dataContext: DataContext?,
  coroutineScope: CoroutineScope?,
  options: NavigationOptions,
  navigateAsync: suspend (NavigationTaskContext) -> Unit,
  navigateBlocking: (NavigationTaskContext) -> Unit,
): Job {
  if (ApplicationManager.getApplication().isDispatchThread) {
    return createNavigationContext(project, options, dataContext)
      .dispatchNavigateRequest(project, coroutineScope, navigateAsync, navigateBlocking)
  }
  return coroutineScope.dispatchNavigateRequest(project, dataContext, options, navigateAsync, navigateBlocking)
}

private fun CoroutineScope?.dispatchNavigateRequest(
  project: Project,
  dataContext: DataContext?,
  options: NavigationOptions,
  navigateAsync: suspend (NavigationTaskContext) -> Unit,
  navigateBlocking: (NavigationTaskContext) -> Unit,
): Job {
  return NavigationTaskCoordinator.getInstance(project).dispatchNavigation(this) {
    val navigateContext = withContext(Dispatchers.EDT) {
      createNavigationContext(project, options, dataContext)
    }
    withContext(navigateContext.coroutineContext) {
      if (isNavigationRequestsEnabled) {
        navigateAsync(navigateContext)
      }
      else {
        withContext(Dispatchers.EDT) {
          navigateBlocking(navigateContext)
        }
      }
    }
  }
}

private inline fun NavigationTaskContext.dispatchNavigateRequest(
  project: Project,
  coroutineScope: CoroutineScope?,
  crossinline navigateAsync: suspend (NavigationTaskContext) -> Unit,
  crossinline navigateBlocking: (NavigationTaskContext) -> Unit,
): Job {
  val coordinator = NavigationTaskCoordinator.getInstance(project)
  return if (isNavigationRequestsEnabled) {
    coordinator.dispatchNavigation(coroutineScope, this) {
      navigateAsync(this@dispatchNavigateRequest)
    }
  }
  else {
    coordinator.dispatchNavigation(coroutineScope, this) {
      withContext(Dispatchers.EDT) {
        navigateBlocking(this@dispatchNavigateRequest)
      }
    }
  }
}

/**
 * Navigates to the specified [navigatable] in a blocking manner, showing a modal progress dialog.
 * This is a blocking version of [NavigationService.navigate].
 */
@RequiresEdt
private fun navigateBlocking(project: Project, navigatable: Navigatable, options: NavigationOptions) {
  ThreadingAssertions.assertEventDispatchThread()
  return runWithModalProgressBlocking(project, IdeBundle.message("progress.title.preparing.navigation")) {
    project.serviceAsync<NavigationService>().navigate(navigatable, options)
  }
}

/**
 * Navigates to the specified navigation [request] in a blocking manner, showing a modal progress dialog.
 * This is a blocking version of [NavigationService.navigate].
 */
@RequiresEdt
private fun navigateBlocking(project: Project, request: NavigationRequest, options: NavigationOptions) {
  ThreadingAssertions.assertEventDispatchThread()
  return runWithModalProgressBlocking(project, IdeBundle.message("progress.title.preparing.navigation")) {
    project.serviceAsync<NavigationService>().navigate(request, options)
  }
}

/**
 * Captures EDT-only navigation inputs (focus/[DataContext], [ModalityState], [ClientId]).
 * [ClientId] is captured only for context propagation into the async navigation, not for lifetime.
 */
@RequiresEdt
@ApiStatus.Internal
private fun createNavigationContext(
  project: Project,
  options: NavigationOptions,
  dataContext: DataContext? = null,
): NavigationTaskContext {
  ThreadingAssertions.assertEventDispatchThread()
  return NavigationTaskContext(
    dataContext = getOrCreateAsyncDataContext(project, dataContext),
    modalityState = ModalityState.current(),
    clientIdContext = ClientId.coroutineContext(),
    requestedOptions = options,
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

/**
 * Maps navigation inputs from this context into [options].
 *
 * A decision the caller already made about [NavigationOptions.requestedEditor] wins over
 * [OpenFileDescriptor.NAVIGATE_IN_EDITOR] from the context.
 */
@ApiStatus.Internal
fun DataContext?.toNavigationOptions(options: NavigationOptions = NavigationOptions.requestFocus()): NavigationOptions {
  if ((options as NavigationOptions.Impl).requestedEditor != RequestedEditor.Unspecified) {
    return options
  }
  val contextEditor = this?.getData(OpenFileDescriptor.NAVIGATE_IN_EDITOR)

  return if (contextEditor == null) {
    options
  } else {
    options.requestedEditor(RequestedEditor.Specific(contextEditor))
  }
}
