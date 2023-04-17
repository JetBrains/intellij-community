// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.navigation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDirectory
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.NonExtendable

@Experimental
@NonExtendable
interface NavigationRequests {

  companion object {

    @JvmStatic
    fun getInstance(): NavigationRequests = ApplicationManager.getApplication().getService(NavigationRequests::class.java)
  }

  /**
   * @return a request for the navigation to a specified [offset] in a [file],
   * or `null` if the navigation is not possible for any reason
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun sourceNavigationRequest(file: VirtualFile, offset: Int): NavigationRequest?

  /**
   * @return a request for the navigation to a specified [directory],
   * or `null` if the navigation is not possible for any reason
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun directoryNavigationRequest(directory: PsiDirectory): NavigationRequest?

  /**
   * @return a request to execute an [arbitrary code][Navigatable.navigate],
   * or `null` if the navigation is not possible for any reason
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  fun rawNavigationRequest(navigatable: Navigatable): NavigationRequest?
}
