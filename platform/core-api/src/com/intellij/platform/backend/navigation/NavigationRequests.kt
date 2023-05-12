// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.navigation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDirectory
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface NavigationRequests {

  companion object {

    @JvmStatic
    fun getInstance(): NavigationRequests = ApplicationManager.getApplication().getService(NavigationRequests::class.java)
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  fun sourceNavigationRequest(file: VirtualFile, offset: Int): NavigationRequest?

  @RequiresReadLock
  @RequiresBackgroundThread
  fun directoryNavigationRequest(directory: PsiDirectory): NavigationRequest?

  /**
   * @return a request to execute an [arbitrary code][Navigatable.navigate],
   * or `null` if the navigation is not possible for any reason
   */
  @Internal
  @Deprecated("Don't call this function directly")
  @RequiresReadLock
  @RequiresBackgroundThread
  fun rawNavigationRequest(navigatable: Navigatable): NavigationRequest?
}
