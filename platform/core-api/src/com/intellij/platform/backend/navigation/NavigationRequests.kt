// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.navigation

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * This interface isn't supposed to be used from plugins, call methods from [NavigationRequest.Companion] instead.
 */
@Internal
interface NavigationRequests {
  companion object {

    @JvmStatic
    fun getInstance(): NavigationRequests = ApplicationManager.getApplication().getService(NavigationRequests::class.java)
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  fun sourceNavigationRequest(project: Project, file: VirtualFile, offset: Int, elementRange: TextRange?): NavigationRequest?

  @RequiresReadLock
  @RequiresBackgroundThread
  fun sharedSourceNavigationRequest(project: Project, file: VirtualFile, context: CodeInsightContext, offset: Int, elementRange: TextRange?): NavigationRequest?

  @RequiresReadLock
  @RequiresBackgroundThread
  fun directoryNavigationRequest(directory: PsiDirectory): NavigationRequest?

  /**
   * An adapted version of [com.intellij.ide.util.EditSourceUtil.getDescriptor].
   */
  @Internal
  @Deprecated("Do not call this function by hand")
  @RequiresReadLock
  @RequiresBackgroundThread
  fun psiNavigationRequest(element: PsiElement): NavigationRequest?

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
