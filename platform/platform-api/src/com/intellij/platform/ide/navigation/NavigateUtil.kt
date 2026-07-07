// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NavigateUtil")

package com.intellij.platform.ide.navigation

import com.intellij.codeWithMe.ClientId
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.IdeUiService
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.client.currentSessionOrNull
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.pom.Navigatable
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * Navigates to the specified [navigatable] in a blocking manner, showing a modal progress dialog.
 * This is a blocking version of [NavigationService.navigate].
 */
@RequiresEdt
@ApiStatus.Obsolete
@ApiStatus.Internal
fun navigateBlocking(project: Project, navigatable: Navigatable, options: NavigationOptions, dataContext: DataContext?) {
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
@ApiStatus.Obsolete
@ApiStatus.Internal
fun navigateBlocking(project: Project, request: NavigationRequest, options: NavigationOptions, dataContext: DataContext?) {
  val dataContext = dataContext ?: fetchDataContext(project)
  return runWithModalProgressBlocking(project, IdeBundle.message("progress.title.preparing.navigation")) {
    project.serviceAsync<NavigationService>().navigate(request, options, dataContext)
  }
}

/**
 * Submits navigation to [navigatable] without blocking the caller.
 *
 * With the `ide.navigation.requests` registry flag enabled, the navigation is launched in [coroutineScope]
 * (or, when none is given, in the scope of the client session identified by the current [ClientId],
 * falling back to the project scope), capturing the current [ClientId] and an async [dataContext].
 * Otherwise, a blocking modal navigation ([navigateBlocking]) is scheduled on a later EDT event.
 *
 * In both modes the function returns before the navigation completes.
 * NB: prefer passing a lifecycle-bound [coroutineScope] when possible.
 */
@RequiresEdt
@ApiStatus.Internal
@JvmOverloads
fun requestNavigate(
  project: Project,
  navigatable: Navigatable,
  options: NavigationOptions = NavigationOptions.defaultOptions(),
  dataContext: DataContext? = null,
  coroutineScope: CoroutineScope? = null,
) {
  if (Registry.`is`("ide.navigation.requests")) {
    val asyncDataContext = getOrCreateAsyncDataContext(project, dataContext)
    launchNavigation(project, coroutineScope) {
      project.serviceAsync<NavigationService>().navigate(navigatable, options, asyncDataContext)
    }
  }
  else {
    ApplicationManager.getApplication().invokeLater {
      navigateBlocking(project, navigatable, options, dataContext)
    }
  }
}

/**
 * Submits navigation to [request] without blocking the caller.
 * See [requestNavigate] for the dispatch semantics.
 */
@RequiresEdt
@ApiStatus.Internal
@JvmOverloads
fun requestNavigate(
  project: Project,
  request: NavigationRequest,
  options: NavigationOptions = NavigationOptions.defaultOptions(),
  dataContext: DataContext? = null,
  coroutineScope: CoroutineScope? = null,
) {
  if (Registry.`is`("ide.navigation.requests")) {
    val asyncDataContext = getOrCreateAsyncDataContext(project, dataContext)
    launchNavigation(project, coroutineScope) {
      project.serviceAsync<NavigationService>().navigate(request, options, asyncDataContext)
    }
  }
  else {
    ApplicationManager.getApplication().invokeLater {
      navigateBlocking(project, request, options, dataContext)
    }
  }
}

private fun launchNavigation(project: Project, coroutineScope: CoroutineScope?, action: suspend () -> Unit) {
  // when no scope is given, prefer the scope of the client session identified by the current ClientId:
  // it is cancelled together with the client (or the project for the local session), which is exactly
  // the lifetime a client-initiated navigation should have
  @Suppress("UsagesOfObsoleteApi")
  val scope = coroutineScope
              ?: (project.currentSessionOrNull as? ComponentManagerEx ?: project as ComponentManagerEx).getCoroutineScope()
  scope.launch(ClientId.coroutineContext()) {
    action()
  }
}

@RequiresEdt
@ApiStatus.Internal
fun getOrCreateAsyncDataContext(project: Project, dataContext: DataContext?): DataContext? {
  val context = dataContext ?: fetchDataContext(project) ?: return null
  return IdeUiService.getInstance().createAsyncDataContext(context)
}

@RequiresEdt
private fun fetchDataContext(project: Project): DataContext? {
  val component = IdeFocusManager.getInstance(project).getFocusOwner()
  return component?.let { DataManager.getInstance().getDataContext(it) }
}
