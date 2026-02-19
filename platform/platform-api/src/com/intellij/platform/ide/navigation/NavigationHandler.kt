// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.platform.backend.navigation.NavigationRequest
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Handles navigation requests in the IDE.
 *
 * Implementations of this interface can be registered via the "com.intellij.navigation.navigationHandler" extension point
 * to provide custom navigation behavior for specific types of [com.intellij.platform.backend.navigation.NavigationRequest]s.
 *
 * The navigation system iterates through all registered handlers and calls their [navigate] method until one of them
 * returns true, indicating that the navigation request has been handled.
 */
@Experimental
interface NavigationHandler {

  /**
   * Attempts to handle the given navigation request.
   *
   * @param request The navigation request to handle. This can be a source navigation request (to navigate to a specific
   *                location in a file), a directory navigation request, or a custom implementation.
   * @param options Options that control the navigation behavior, such as whether to request focus, preserve caret position, etc.
   * @param dataContext The data context from which the navigation was initiated, which may contain additional information
   *                    needed for navigation.
   * @return true if the navigation request was handled by this handler, false otherwise.
   */
  fun navigate(request: NavigationRequest, options: NavigationOptions, dataContext: DataContext): Boolean
}