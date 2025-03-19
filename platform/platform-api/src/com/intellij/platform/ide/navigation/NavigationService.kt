// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.pom.Navigatable
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

@Experimental
interface NavigationService {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): NavigationService {
      return project.service<NavigationService>()
    }
  }

  suspend fun navigate(dataContext: DataContext, options: NavigationOptions)

  @Internal
  suspend fun navigate(request: NavigationRequest, options: NavigationOptions = NavigationOptions.defaultOptions())

  @Internal // compatibility function
  suspend fun navigate(navigatables: List<Navigatable>, options: NavigationOptions): Boolean

  @Internal // compatibility function
  suspend fun navigate(navigatable: Navigatable, options: NavigationOptions = NavigationOptions.defaultOptions()): Boolean
}
