// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.navigation.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextManager
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled
import com.intellij.codeInsight.navigation.shouldOpenAsNative
import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.editor.LazyRangeMarkerFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationRequests
import com.intellij.pom.Navigatable
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.concurrency.ThreadingAssertions

private class NavigationRequestsImpl : NavigationRequests {
  override fun sourceNavigationRequest(project: Project, file: VirtualFile, offset: Int, elementRange: TextRange?): NavigationRequest? {
    return sharedSourceNavigationRequest(project, file, anyContext(), offset, elementRange)
  }

  override fun sharedSourceNavigationRequest(project: Project, file: VirtualFile, context: CodeInsightContext, offset: Int, elementRange: TextRange?): NavigationRequest? {
    ThreadingAssertions.assertReadAccess()
    ThreadingAssertions.assertBackgroundThread()
    if (!file.isValid) {
      return null
    }
    // TODO ? check if offset is within bounds
    val offsetMarker = if (offset >= 0) {
      LazyRangeMarkerFactory.getInstance(project).createRangeMarker(file, offset)
    }
    else {
      null
    }
    val elementRangeMarker = if (elementRange != null) {
      FileDocumentManager.getInstance().getDocument(file, project)?.createRangeMarker(elementRange)
    }
    else {
      null
    }

    return SharedSourceNavigationRequest(file, context, offsetMarker, elementRangeMarker)
  }

  override fun directoryNavigationRequest(directory: PsiDirectory): NavigationRequest? {
    ThreadingAssertions.assertReadAccess()
    ThreadingAssertions.assertBackgroundThread()
    if (!directory.isValid) {
      return null
    }
    return DirectoryNavigationRequest(directory)
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun psiNavigationRequest(element: PsiElement): NavigationRequest? {
    ThreadingAssertions.assertReadAccess()
    ThreadingAssertions.assertBackgroundThread()

    val originalElement = EditSourceUtil.getNavigatableOriginalElement(element) ?: element
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

    val virtualFile = PsiUtilCore.getVirtualFile(navigationElement)?.takeIf { it.isValid } ?: return null
    if (navigationElement is PsiFile && shouldOpenAsNative(virtualFile)) {
      @Suppress("DEPRECATION")
      return rawNavigationRequest(navigationElement)
    }
    return when (navigationElement) {
      is PsiDirectory -> {
        DirectoryNavigationRequest(navigationElement)
      }
      else -> {
        val project = element.project
        if (isSharedSourceSupportEnabled(project)) {
          val navigationFileViewProvider = navigationElement.containingFile?.viewProvider

          val context = if (isSharedSourceSupportEnabled(project) && navigationFileViewProvider != null) {
            val contextManager = CodeInsightContextManager.getInstance(navigationElement.project)
            contextManager.getCodeInsightContext(navigationFileViewProvider)
          }
          else anyContext()

          sharedSourceNavigationRequest(
            project = project,
            file = virtualFile,
            context = context,
            offset = navigationElement.textOffset, // this triggers decompiler if [virtualFile] corresponds to a .class file
            elementRange = navigationElement.textRange,
          )
        }
        else {
          sourceNavigationRequest(
            project = project,
            file = virtualFile,
            offset = navigationElement.textOffset, // this triggers decompiler if [virtualFile] corresponds to a .class file
            elementRange = navigationElement.textRange,
          )
        }
      }
    }
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun rawNavigationRequest(navigatable: Navigatable): NavigationRequest? {
    ThreadingAssertions.assertReadAccess()
    ThreadingAssertions.assertBackgroundThread()
    return if (navigatable.canNavigate()) RawNavigationRequest(navigatable, navigatable.canNavigateToSource()) else null
  }
}
