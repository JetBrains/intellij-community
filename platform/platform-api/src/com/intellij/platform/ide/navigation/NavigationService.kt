// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.navigation

import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.pom.Navigatable
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus.*

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
  suspend fun navigate(
    request: NavigationRequest,
    options: NavigationOptions = NavigationOptions.defaultOptions(),
    dataContext: DataContext? = null,
  )

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

/**
 * Navigates to the specified [navigatable] in a blocking manner, showing a modal progress dialog.
 * This is a blocking version of [NavigationService.navigate].
 */
@RequiresEdt
@Obsolete
@Internal
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
@Obsolete
@Internal
fun navigateBlocking(project: Project, request: NavigationRequest, options: NavigationOptions, dataContext: DataContext?) {
  val dataContext = dataContext ?: fetchDataContext(project)
  return runWithModalProgressBlocking(project, IdeBundle.message("progress.title.preparing.navigation")) {
    project.serviceAsync<NavigationService>().navigate(request, options, dataContext)
  }
}

@RequiresEdt
private fun fetchDataContext(project: Project): DataContext? {
  val component = IdeFocusManager.getInstance(project).getFocusOwner()
  return component?.let { DataManager.getInstance().getDataContext(it) }
}
