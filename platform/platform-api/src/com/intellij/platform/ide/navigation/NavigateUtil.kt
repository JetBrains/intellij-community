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
import com.intellij.util.runIf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

private val isNavigationRequestsEnabled: Boolean
  get() = Registry.`is`("ide.navigation.requests")

/**
 * Submits navigation to [navigatable] without waiting for it to finish.
 *
 * Use this function to start navigation from an action or another UI handler.
 * [NavigationService] computes [Navigatable.navigationRequest] on a background thread under a read action.
 * Pass a navigatable that resolves its target lazily when the resolution needs PSI access.
 * Existing [Navigatable.navigate] implementations remain supported through raw navigation requests.
 *
 * Call this function outside a write action.
 * UI context is captured immediately for EDT callers and asynchronously for callers on other threads.
 *
 * Tests which depend on navigation started outside those fixtures (e.g., via `EditorTestUtil.executeAction`)
 * must explicitly await the pending-navigation barrier (see `NavigationTestUtil.awaitPendingNavigation`)
 * outside a write action.
 * NB: prefer passing a lifecycle-bound [coroutineScope] when possible.
 *
 * Cancelling the returned future cancels the navigation task.
 *
 * @return a future containing `true` if at least one request was handled
 * Failure or cancellation completes the future exceptionally.
 */
@ApiStatus.Internal
@JvmOverloads
fun requestNavigate(
  project: Project,
  navigatable: Navigatable,
  options: NavigationOptions = NavigationOptions.defaultOptions(),
  dataContext: DataContext? = null,
  coroutineScope: CoroutineScope? = null,
): CompletableFuture<Boolean> {
  return dispatchNavigateRequest(project, options, dataContext, coroutineScope) { ctx ->
    navigate(navigatable, ctx.navigationOptions)
  }
}

/**
 * Submits navigation to [navigatables] without waiting for it to finish.
 * @see [requestNavigate] for the dispatch and completion semantics.
 */
@ApiStatus.Internal
@JvmOverloads
fun requestNavigate(
  project: Project,
  navigatables: List<Navigatable>,
  options: NavigationOptions = NavigationOptions.defaultOptions(),
  dataContext: DataContext? = null,
  coroutineScope: CoroutineScope? = null,
): CompletableFuture<Boolean> {
  return dispatchNavigateRequest(project, options, dataContext, coroutineScope) { ctx ->
    navigate(navigatables, ctx.navigationOptions)
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
): CompletableFuture<Boolean> {
  return dispatchNavigateRequest(
    project,
    options,
    dataContext,
    coroutineScope
  ) { ctx ->
    navigate(request, ctx.navigationOptions)
  }
}

/**
 * Submits navigation to the requests resolved by [supplier] without waiting for it to finish.
 *
 * The supplier runs on a background thread inside the navigation task, without an implicit read action.
 * Use [readAction] inside the supplier when resolving targets needs PSI access.
 * The supplier must return requests without starting another navigation.
 *
 * @see [requestNavigate] for the dispatch and completion semantics.
 * @see [NavigationService.navigateRequests]
 */
@ApiStatus.Internal
fun requestNavigate(
  project: Project,
  options: NavigationOptions = NavigationOptions.defaultOptions(),
  dataContext: DataContext? = null,
  coroutineScope: CoroutineScope? = null,
  supplier: suspend () -> Collection<NavigationRequest>,
): CompletableFuture<Boolean> {
  return dispatchNavigateRequest(
    project,
    options,
    dataContext,
    coroutineScope
  ) { ctx ->
    navigateRequests(ctx.navigationOptions, supplier)
  }
}

/**
 * Submits navigation to the targets from [dataContext] without waiting for it to finish.
 * @see [requestNavigate] for the dispatch and completion semantics.
 */
@ApiStatus.Internal
@JvmOverloads
fun requestNavigate(
  project: Project,
  dataContext: DataContext,
  options: NavigationOptions = NavigationOptions.defaultOptions(),
  coroutineScope: CoroutineScope? = null,
): CompletableFuture<Boolean> {
  return requestNavigate(project, dataContext, options, whenPerformed = null, coroutineScope = coroutineScope)
}

/**
 * Submits navigation from [dataContext] and runs [whenPerformed] on the EDT after normal completion, including a `false` result.
 * The callback runs inside the navigation task, before the future completes and the task leaves navigation tracking.
 */
@ApiStatus.Internal
fun requestNavigate(
  project: Project,
  dataContext: DataContext,
  options: NavigationOptions,
  whenPerformed: Runnable?,
  coroutineScope: CoroutineScope? = null,
): CompletableFuture<Boolean> {
  return dispatchNavigateRequest(
    project,
    options,
    dataContext,
    coroutineScope
  ) { ctx ->
    val handled = navigate(project, ctx.dataContext, ctx.navigationOptions)
    if (whenPerformed != null) {
      withContext(Dispatchers.EDT) {
        whenPerformed.run()
      }
    }
    handled
  }
}

/**
 * The targets are resolved from async [dataContext] inside the navigation task, so a navigation which is still resolving them
 * is canceled by a newer one instead of outliving it.
 */
private suspend fun navigate(project: Project, dataContext: DataContext?, options: NavigationOptions): Boolean {
  return project.serviceAsync<NavigationService>().navigate(options) {
    readAction {
      dataContext?.getData(CommonDataKeys.NAVIGATABLE_ARRAY)?.toList()
    }.orEmpty()
  }
}

private inline fun dispatchNavigateRequest(
  project: Project,
  options: NavigationOptions,
  dataContext: DataContext?,
  coroutineScope: CoroutineScope?,
  crossinline navigate: suspend NavigationService.(NavigationTaskContext) -> Boolean,
): CompletableFuture<Boolean> {
  val coordinator = NavigationTaskCoordinator.getInstance(project)
  val precomputedContext = runIf(ApplicationManager.getApplication().isDispatchThread) {
    createNavigationContext(project, options, dataContext)
  }

  return coordinator.dispatchNavigation(coroutineScope, precomputedContext) {
    val context = precomputedContext ?: withContext(Dispatchers.EDT) {
      createNavigationContext(project, options, dataContext)
    }
    withContext(context.coroutineContext) {
      val service = project.serviceAsync<NavigationService>()
      if (isNavigationRequestsEnabled) {
        service.navigate(context)
      }
      else {
        withContext(Dispatchers.EDT) {
          runWithModalProgressBlocking(project, IdeBundle.message("progress.title.preparing.navigation")) {
            service.navigate(context)
          }
        }
      }
    }
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
 * Uses [NavigationOptions.KEY] when [options] is omitted, or the default options when the context has no options.
 *
 * A decision the caller already made about [NavigationOptions.requestedEditor] wins over
 * [OpenFileDescriptor.NAVIGATE_IN_EDITOR] from the context.
 */
@ApiStatus.Internal
fun DataContext?.toNavigationOptions(
  options: NavigationOptions = this?.let { NavigationOptions.fromContext(it) } ?: NavigationOptions.defaultOptions(),
): NavigationOptions {
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
