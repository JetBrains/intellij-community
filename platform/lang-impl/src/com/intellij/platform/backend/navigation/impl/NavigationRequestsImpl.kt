// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.navigation.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextManager
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled
import com.intellij.codeInsight.navigation.shouldOpenAsNative
import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.EditSourceUtil
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.LazyRangeMarkerFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.withProgressReport
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationRequests
import com.intellij.pom.Navigatable
import com.intellij.pom.PomTargetPsiElement
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.concurrency.ThreadingAssertions

internal class NavigationRequestsImpl : NavigationRequests {
  override fun sourceNavigationRequest(project: Project, file: VirtualFile, offset: Int, elementRange: TextRange?): NavigationRequest? {
    return sharedSourceNavigationRequest(project, file, anyContext(), offset, elementRange)
  }

  override fun sharedSourceNavigationRequest(project: Project, file: VirtualFile, context: CodeInsightContext, offset: Int, elementRange: TextRange?): NavigationRequest? {
    return createSourceRequest(project, file, context, offset, elementRange)
  }

  private fun createSourceRequest(
    project: Project,
    file: VirtualFile,
    context: CodeInsightContext,
    offset: Int,
    elementRange: TextRange?,
    lazyDecompilerElement: SmartPsiElementPointer<PsiElement>? = null,
    useCurrentWindow: Boolean = false,
    usePreviewTab: Boolean = false,
  ): NavigationRequest? {
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
      val document = FileDocumentManager.getInstance().getDocument(file, project)
      if (document != null && elementRange.endOffset >= document.textLength) null
      else document?.createRangeMarker(elementRange)
    }
    else {
      null
    }

    return SharedSourceNavigationRequest(
      file = file,
      context = context,
      offsetMarker = offsetMarker,
      elementRangeMarker = elementRangeMarker,
      initialOffset = offset.takeIf { it >= 0 },
      initialFileStamp = file.modificationStamp,
      initialDocumentStamp = FileDocumentManager.getInstance().getCachedDocument(file)?.modificationStamp,
      lazyDecompilerElement = lazyDecompilerElement,
      useCurrentWindow = useCurrentWindow,
      usePreviewTab = usePreviewTab,
    )
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
        val useCurrentWindow = FileEditorManager.USE_CURRENT_WINDOW.isIn(navigationElement)
        val usePreviewTab = UISettings.getInstance().openInPreviewTabIfPossible && Registry.`is`("editor.preview.tab.navigation")
        val deferDecompilerOffset = BinaryFileTypeDecompilers.getInstance().hasDecompiler(virtualFile) &&
                                     FileDocumentManager.getInstance().getCachedDocument(virtualFile) == null &&
                                     Registry.`is`("hyperlink.ide.decompiler.open.file")
        if (deferDecompilerOffset) {
          return createSourceRequest(
            project = project,
            file = virtualFile,
            context = anyContext(),
            offset = -1,
            elementRange = null,
            lazyDecompilerElement = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(navigationElement),
            useCurrentWindow = useCurrentWindow,
            usePreviewTab = usePreviewTab,
          )
        }
        val (offset, elementRange) = withProgressReport {
          navigationElement.textOffset to navigationElement.textRange
        }
        val context = if (isSharedSourceSupportEnabled(project)) {
          // The navigation opens the host file of an injected element, so the request carries the context of the host file.
          val navigationFileViewProvider = InjectedLanguageManager.getInstance(project).getTopLevelFile(navigationElement)?.viewProvider
          if (navigationFileViewProvider != null) {
            CodeInsightContextManager.getInstance(navigationElement.project).getCodeInsightContext(navigationFileViewProvider)
          }
          else {
            anyContext()
          }
        }
        else {
          anyContext()
        }
        createSourceRequest(
          project = project,
          file = virtualFile,
          context = context,
          offset = offset,
          elementRange = elementRange,
          useCurrentWindow = useCurrentWindow,
          usePreviewTab = usePreviewTab,
        )
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
