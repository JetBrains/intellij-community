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
   * Initiates navigation, resolving the requests lazily via [supplier].
   *
   * The supplier is invoked exactly once, on a background thread inside the navigation task: resolving the target is therefore
   * awaited together with the navigation, but it runs before this navigation replaces the one which is currently running.
   * A resolution which lost to a newer navigation is cancelled once the newer one knows it has something to navigate to;
   * if the losing resolution has already completed, its result is discarded instead.
   * The supplier may perform `ReadAction`s, but it must not initiate another navigation:
   * a nested navigation is reported as an error and does nothing.
   *
   * The resolved requests are navigated as one batch: serialized as a whole, at most one
   * back-history entry, and a single split when [NavigationOptions.openInRightSplit] is enabled.
   *
   * @param options Contains configuration settings and parameters that influence the navigation behavior.
   * @param supplier providing [NavigationRequest]s; an empty result means no navigation happens.
   * @return `true` if at least one resolved request was handled
   */
  suspend fun navigateRequests(
    options: NavigationOptions,
    supplier: suspend () -> Collection<NavigationRequest>,
  ): Boolean {
    return navigate(supplier(), options)
  }

  suspend fun navigateRequests(
    supplier: suspend () -> Collection<NavigationRequest>,
  ): Boolean {
    return navigateRequests(NavigationOptions.defaultOptions(), supplier)
  }

  /**
   * Initiates navigation based on the provided request, with optional navigation options.
   *
   * @param request The navigation request describing the destination and associated parameters.
   * @param options Optional navigation options to customize the navigation behavior. Defaults to `NavigationOptions.defaultOptions()`.
   * @return `true` if at least one request was handled
   *
   * @see NavigationRequest
   */
  suspend fun navigate(
    request: NavigationRequest,
    options: NavigationOptions,
  ): Boolean

  suspend fun navigate(request: NavigationRequest): Boolean {
    return navigate(request, NavigationOptions.defaultOptions())
  }

  /**
   * Navigates to a batch of [requests] as one operation.
   * The batch is serialized as a whole and produces at most one back-history entry.
   * If [NavigationOptions.openInRightSplit] is enabled, all requests are opened in a single new split.
   *
   * @return `true` if at least one request was handled
   */
  suspend fun navigate(
    requests: Collection<NavigationRequest>,
    options: NavigationOptions,
  ): Boolean

  suspend fun navigate(requests: Collection<NavigationRequest>): Boolean {
    return navigate(requests, NavigationOptions.defaultOptions())
  }

  /**
   * Navigates to a batch of [navigatables] as one operation.
   *
   * @return `true` if at least one navigatable was handled
   */
  @Internal // compatibility function
  suspend fun navigate(
    navigatables: List<Navigatable>,
    options: NavigationOptions = NavigationOptions.defaultOptions(),
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
  suspend fun navigate(navigatable: Navigatable, options: NavigationOptions): Boolean {
    return navigate(listOf(navigatable), options)
  }

  @Deprecated(
    "Prefer NavigationOptions instead of DataContext",
    ReplaceWith("navigate(request, dataContext.toNavigationOptions(options))"),
  )
  suspend fun navigate(request: NavigationRequest, options: NavigationOptions, dataContext: DataContext?): Boolean {
    if (dataContext == null) {
      return navigate(request, options)
    }
    val effectiveOptions = dataContext.toNavigationOptions(options)
    return navigate(request, effectiveOptions)
  }
}
