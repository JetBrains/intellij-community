// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.pom.Navigatable
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal

@ApiStatus.NonExtendable
interface NavigationService {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): NavigationService {
      return project.service<NavigationService>()
    }

    /**
     * Convenience handle
     *
     * @see [NavigationTaskCoordinator.pendingNavigation]
     */
    @Internal
    @JvmStatic
    fun pendingNavigation(project: Project): Job {
      return NavigationTaskCoordinator.getInstance(project).pendingNavigation()
    }
  }

  /**
   * Initiates navigation in UI based on the provided data context and navigation options.
   *
   * @param dataContext Represents the contextual information required for determining the navigation target.
   * @see [com.intellij.ide.ui.IdeUiService.createAsyncDataContext].
   * @param options Contains configuration settings and parameters that influence the navigation behavior.
   * @return `true` if at least one navigation target was handled
   */
  suspend fun navigate(dataContext: DataContext, options: NavigationOptions): Boolean

  /**
   * Initiates navigation, resolving the requests lazily via [supplier].
   *
   * The supplier is invoked exactly once, on a background thread inside the navigation task: resolving the target is therefore
   * awaited together with the navigation, and a newer navigation cancels a resolution which is still running.
   * The supplier may perform read actions, but it must not initiate another navigation:
   * a nested navigation request cancels the current one.
   *
   * The resolved requests are navigated as one batch: serialized as a whole, at most one
   * back-history entry, and a single split when [NavigationOptions.openInRightSplit] is enabled.
   *
   * @param options Contains configuration settings and parameters that influence the navigation behavior.
   * @param supplier providing [NavigationRequest]s; an empty result means no navigation happens.
   * @return `true` if at least one resolved request was handled
   */
  suspend fun navigateRequests(
    options: NavigationOptions = NavigationOptions.defaultOptions(),
    supplier: suspend () -> Collection<NavigationRequest>,
  ): Boolean {
    return navigate(supplier(), options)
  }

  /**
   * Initiates navigation based on the provided request, with optional navigation options and a data context.
   *
   * @param request The navigation request describing the destination and associated parameters.
   * @param options Optional navigation options to customize the navigation behavior. Defaults to `NavigationOptions.defaultOptions()`.
   * @param dataContext Optional context data to provide additional information or state during navigation. Can be null.
   * @return `true` if at least one request was handled
   *
   * @see NavigationRequest
   */
  suspend fun navigate(
    request: NavigationRequest,
    options: NavigationOptions = NavigationOptions.defaultOptions(),
    dataContext: DataContext? = null,
  ): Boolean

  /**
   * Navigates to a batch of [requests] as one operation.
   * The batch is serialized as a whole and produces at most one back-history entry.
   * If [NavigationOptions.openInRightSplit] is enabled, all requests are opened in a single new split.
   *
   * @return `true` if at least one request was handled
   */
  suspend fun navigate(
    requests: Collection<NavigationRequest>,
    options: NavigationOptions = NavigationOptions.defaultOptions(),
    dataContext: DataContext? = null,
  ): Boolean

  /**
   * Navigates to a batch of [navigatables] as one operation.
   *
   * @return `true` if at least one navigatable was handled
   */
  @Internal // compatibility function
  suspend fun navigate(
    navigatables: List<Navigatable>,
    options: NavigationOptions = NavigationOptions.defaultOptions(),
    dataContext: DataContext? = null,
  ): Boolean

  /**
   * Same semantics as [navigateRequests].
   */
  @Internal
  suspend fun navigate(
    options: NavigationOptions = NavigationOptions.defaultOptions(),
    supplier: suspend () -> Collection<Navigatable>,
  ): Boolean {
    return navigate(supplier().toList(), options)
  }

  /**
   * @return `true` if [navigatable] was handled
   */
  @Internal // compatibility function
  suspend fun navigate(navigatable: Navigatable, options: NavigationOptions, dataContext: DataContext? = null): Boolean {
    return navigate(listOf(navigatable), options, dataContext)
  }
}
