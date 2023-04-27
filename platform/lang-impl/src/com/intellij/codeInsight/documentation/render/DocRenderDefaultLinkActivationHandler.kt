// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.documentation.QuickDocUtil
import com.intellij.ide.BrowserUtil
import com.intellij.lang.documentation.ide.impl.DocumentationManager.Companion.instance
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.util.Disposer
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.ui.popup.PopupFactoryImpl
import java.awt.geom.Rectangle2D
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.text.BadLocationException

internal object DocRenderDefaultLinkActivationHandler : DocRenderLinkActivationHandler {
  override fun activateLink(event: HyperlinkEvent, renderer: DocRenderer) {
    val location = getLocation(event) ?: return
    val item = renderer.item
    val url = event.description
    if (QuickDocUtil.isDocumentationV2Enabled()) {
      activateLinkV2(url, location, renderer)
      return
    }
    val documentation = item.getInlineDocumentation() ?: return
    val context = (documentation as PsiCommentInlineDocumentation).context
    if (isGotoDeclarationEvent) {
      navigateToDeclaration(context, url)
    }
    else {
      showDocumentation(item.editor, context, url, location, renderer)
    }
  }

  private fun activateLinkV2(url: String, location: Rectangle2D, renderer: DocRenderer) {
    val item = renderer.item
    val editor = item.editor
    val project = editor.project ?: return
    if (isGotoDeclarationEvent) {
      instance(project).navigateInlineLink(
        url) { item.getInlineDocumentationTarget() }
    }
    else {
      instance(project).activateInlineLink(
        url, { item.getInlineDocumentationTarget() },
        editor, popupPosition(location, renderer)
      )
    }
  }

  @Deprecated("Unused in v2 implementation.")
  private fun showDocumentation(editor: Editor,
                                context: PsiElement,
                                linkUrl: String,
                                linkLocationWithinInlay: Rectangle2D,
                                renderer: DocRenderer) {
    if (isExternalLink(linkUrl)) {
      BrowserUtil.open(linkUrl)
      return
    }
    val project = context.project
    val documentationManager = DocumentationManager.getInstance(project)
    if (QuickDocUtil.getActiveDocComponent(project) == null) {
      editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POINT,
                         popupPosition(linkLocationWithinInlay, renderer))
      documentationManager.showJavaDocInfo(editor, context, context, { editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POINT, null) }, "",
                                           false, true)
    }
    val component = QuickDocUtil.getActiveDocComponent(project)
    if (component != null) {
      if (!documentationManager.hasActiveDockedDocWindow()) {
        component.startWait()
      }
      documentationManager.navigateByLink(component, context, linkUrl)
    }
    if (documentationManager.docInfoHint == null) {
      editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POINT, null)
    }
    if (documentationManager.hasActiveDockedDocWindow()) {
      val disposable = Disposer.newDisposable()
      editor.caretModel.addCaretListener(object : CaretListener {
        override fun caretPositionChanged(e: CaretEvent) {
          Disposer.dispose(disposable)
        }
      }, disposable)
      documentationManager.muteAutoUpdateTill(disposable)
    }
  }

  private fun navigateToDeclaration(context: PsiElement, linkUrl: String) {
    val targetElement = DocumentationManager.getInstance(context.project).getTargetElement(context, linkUrl)
    if (targetElement is Navigatable) {
      (targetElement as Navigatable).navigate(true)
    }
  }

  private fun isExternalLink(linkUrl: String): Boolean {
    val l = linkUrl.lowercase()
    return l.startsWith("http://") || l.startsWith("https://")
  }
}