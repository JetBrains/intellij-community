// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.lang.documentation.ide.impl

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.lang.documentation.ide.impl.DocumentationBrowser.Companion.waitForContent
import com.intellij.lang.documentation.ide.ui.DEFAULT_UI_RESPONSE_TIMEOUT
import com.intellij.lang.documentation.ide.ui.DocumentationPopupUI
import com.intellij.lang.documentation.ide.ui.DocumentationUI
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.DocumentationHoverInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.PopupBridge
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.documentation.impl.documentationRequest
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.util.PsiUtilBase
import com.intellij.ui.popup.AbstractPopup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.swing.JComponent

internal fun calcTargetDocumentationInfo(project: Project, hostEditor: Editor, hostOffset: Int): DocumentationHoverInfo? {
  ApplicationManager.getApplication().assertIsNonDispatchThread()
  return runBlockingCancellable {
    val requests = readAction {
      val targets = injectedThenHost(
        project, hostEditor, hostOffset,
        IdeDocumentationTargetProvider.getInstance(project)::documentationTargets
      )
      targets?.map { it.documentationRequest() }
    }
    if (requests.isNullOrEmpty()) {
      return@runBlockingCancellable null
    }
    if (!LightEdit.owns(project)) {
      val preview = withContext(Dispatchers.EDT) {
        DocumentationToolWindowManager.getInstance(project).updateVisibleAutoUpdatingTab(requests.first())
      }
      if (preview) {
        return@runBlockingCancellable null
      }
    }
    val browser = DocumentationBrowser.createBrowser(project, requests)
    val hasContent: Boolean? = withTimeoutOrNull(DEFAULT_UI_RESPONSE_TIMEOUT) {
      // to avoid flickering: wait a bit before showing the hover popup,
      // otherwise, the popup will be shown with "Fetching..." message,
      // which will immediately get replaced with loaded docs
      browser.waitForContent()
    }
    if (hasContent != null && !hasContent) {
      // If we are sure that there is nothing to show, then return `null`.
      return@runBlockingCancellable null
    }
    // Other two possibilities:
    // - we don't know yet because DEFAULT_UI_RESPONSE_TIMEOUT wasn't enough to compute the doc, or
    // - we do know that there is something to show.
    DocumentationTargetHoverInfo(browser)
  }
}

fun <X : Any> injectedThenHost(project: Project, hostEditor: Editor, hostOffset: Int, f: (Editor, PsiFile, Int) -> X?): X? {
  val hostFile = PsiUtilBase.getPsiFileInEditor(hostEditor, project)
                 ?: return null
  return tryInjected(project, hostFile, hostEditor, hostOffset, f)
         ?: f(hostEditor, hostFile, hostOffset)
}

private fun <X : Any> tryInjected(
  project: Project,
  hostFile: PsiFile,
  hostEditor: Editor,
  hostOffset: Int,
  f: (Editor, PsiFile, Int) -> X?
): X? {
  val injectedLeaf = InjectedLanguageManager.getInstance(project).findInjectedElementAt(hostFile, hostOffset)
                     ?: return null
  val injectedFile = injectedLeaf.containingFile
  val injectedEditor = InjectedLanguageUtil.getInjectedEditorForInjectedFile(hostEditor, injectedFile) as? EditorWindow
                       ?: return null
  val injectedOffset = injectedEditor.document.hostToInjected(hostOffset)
  return f(injectedEditor, injectedFile, injectedOffset)
}

private class DocumentationTargetHoverInfo(
  private val browser: DocumentationBrowser,
) : DocumentationHoverInfo {

  override fun showInPopup(project: Project): Boolean = true

  override fun createQuickDocComponent(editor: Editor, jointPopup: Boolean, bridge: PopupBridge): JComponent {
    val project = editor.project!!
    val documentationUI = DocumentationUI(project, browser, isPopup = true)
    val popupUI = DocumentationPopupUI(project, documentationUI)
    if (jointPopup) {
      popupUI.jointHover()
    }
    bridge.performWhenAvailable { popup: AbstractPopup ->
      popupUI.setPopup(popup)
      popupUI.updatePopup {
        resizePopup(popup, it)
        writeIntentReadAction {
          bridge.updateLocation()
        }
      }
      val fileType = editor.virtualFile?.fileType
      DocumentationUsageCollector.QUICK_DOC_SHOWN.log(fileType)
      val startTime = System.currentTimeMillis()
      Disposer.register(popup) {
        DocumentationUsageCollector.QUICK_DOC_CLOSED.log(fileType, jointPopup, System.currentTimeMillis() - startTime)
      }

    }
    EditorUtil.disposeWithEditor(editor, popupUI)
    return popupUI.component
  }
}
