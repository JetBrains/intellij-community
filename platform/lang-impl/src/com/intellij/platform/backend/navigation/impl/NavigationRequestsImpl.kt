// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.navigation.impl

import com.intellij.codeInsight.navigation.NavigationUtil.shouldOpenAsNative
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationRequests
import com.intellij.pom.Navigatable
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore

internal class NavigationRequestsImpl : NavigationRequests {

  override fun sourceNavigationRequest(file: VirtualFile, offset: Int): NavigationRequest? {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    if (!file.isValid) {
      return null
    }
    // TODO ? check if offset is within bounds
    return SourceNavigationRequest(file, offset)
  }

  override fun directoryNavigationRequest(directory: PsiDirectory): NavigationRequest? {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    if (!directory.isValid) {
      return null
    }
    return DirectoryNavigationRequest(directory)
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun psiNavigationRequest(element: PsiElement): NavigationRequest? {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    val originalElement = EditSourceUtil.getNavigatableOriginalElement(element)
                          ?: element
    if (!EditSourceUtil.canNavigate(originalElement)) {
      return null
    }
    if (originalElement is PomTargetPsiElement) {
      return originalElement.target.navigationRequest()
    }
    val navigationElement = originalElement.navigationElement
    if (navigationElement is PomTargetPsiElement) {
      return navigationElement.target.navigationRequest()
    }
    val virtualFile = PsiUtilCore.getVirtualFile(navigationElement)
    if (virtualFile == null || !virtualFile.isValid) {
      return null
    }
    if (navigationElement is PsiFile && shouldOpenAsNative(virtualFile)) {
      @Suppress("DEPRECATION")
      return rawNavigationRequest(navigationElement)
    }
    return when (navigationElement) {
      is PsiDirectory -> {
        DirectoryNavigationRequest(navigationElement)
      }
      else -> {
        SourceNavigationRequest(
          virtualFile,
          navigationElement.textOffset, // this triggers decompiler if [virtualFile] corresponds to a .class file
        )
      }
    }
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun rawNavigationRequest(navigatable: Navigatable): NavigationRequest? {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    if (!navigatable.canNavigateToSource() && !navigatable.canNavigate()) {
      return null
    }
    return RawNavigationRequest(navigatable)
  }
}
