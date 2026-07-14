// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.pom.Navigatable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal

@ApiStatus.NonExtendable
interface NavigationService {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): NavigationService {
      return project.service<NavigationService>()
    }
  }

  /**
   * Initiates navigation in UI based on the provided data context and navigation options.
   *
   * @param dataContext Represents the contextual information required for determining the navigation target.
   * @param options Contains configuration settings and parameters that influence the navigation behavior.
   */
  suspend fun navigate(dataContext: DataContext, options: NavigationOptions): Boolean

  /**
   * Initiates navigation based on the provided request, with optional navigation options and a data context.
   *
   * @param request The navigation request describing the destination and associated parameters.
   * @param options Optional navigation options to customize the navigation behavior. Defaults to `NavigationOptions.defaultOptions()`.
   * @param dataContext Optional context data to provide additional information or state during navigation. Can be null.
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

  @Internal // compatibility function
  suspend fun navigate(
    navigatables: List<Navigatable>,
    options: NavigationOptions = NavigationOptions.defaultOptions(),
    dataContext: DataContext? = null,
  ): Boolean

  @Internal // compatibility function
  suspend fun navigate(navigatable: Navigatable, options: NavigationOptions, dataContext: DataContext? = null): Boolean {
    return navigate(listOf(navigatable), options, dataContext)
  }
}
