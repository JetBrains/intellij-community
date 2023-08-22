// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.lang.documentation.ide.impl.DocumentationBrowser.Companion.waitForContent
import com.intellij.lang.documentation.ide.ui.DEFAULT_UI_RESPONSE_TIMEOUT
import com.intellij.lang.documentation.ide.ui.DocumentationPopupUI
import com.intellij.lang.documentation.ide.ui.DocumentationUI
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.documentation.impl.DocumentationRequest
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun showDocumentationPopup(
  project: Project,
  request: DocumentationRequest,
  popupContext: PopupContext,
): AbstractPopup {
  val browser = DocumentationBrowser.createBrowser(project, request)
  try {
    // to avoid flickering: show popup after there is anything to show
    // OR show popup after the timeout
    withTimeoutOrNull(DEFAULT_UI_RESPONSE_TIMEOUT) {
      browser.waitForContent()
    }
  }
  catch (ce: CancellationException) {
    Disposer.dispose(browser)
    throw ce
  }
  val popupUI = DocumentationPopupUI(project, DocumentationUI(project, browser))
  val popup = createDocumentationPopup(project, popupUI, popupContext)
  try {
    popupContext.setUpPopup(popup, popupUI)
  }
  catch (ce: CancellationException) {
    Disposer.dispose(popup)
    throw ce
  }
  val boundsHandler = popupContext.boundsHandler()
  val resized = popupUI.useStoredSize()
  popupUI.updatePopup {
    boundsHandler.updatePopup(popup, resized.get())
  }
  check(popup.canShow()) // sanity check
  boundsHandler.showPopup(popup)
  return popup
}

private fun createDocumentationPopup(
  project: Project,
  popupUI: DocumentationPopupUI,
  popupContext: PopupContext,
): AbstractPopup {
  EDT.assertIsEdt()
  val builder = JBPopupFactory.getInstance()
    .createComponentPopupBuilder(popupUI.component, popupUI.preferableFocusComponent)
    .setProject(project)
    .addUserData(ClientId.current)
    .setResizable(true)
    .setMovable(true)
    .setFocusable(true)
    .setModalContext(false)
  popupContext.preparePopup(builder)
  val popup = builder.createPopup() as AbstractPopup
  popupUI.setPopup(popup)
  return popup
}

internal fun resizePopup(popup: AbstractPopup) {
  popup.size = popup.component.preferredSize
}
