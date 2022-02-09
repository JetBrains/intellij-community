// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.lang.documentation.ide.impl.DocumentationBrowser.Companion.waitForContent
import com.intellij.lang.documentation.ide.ui.DEFAULT_UI_RESPONSE_TIMEOUT
import com.intellij.lang.documentation.ide.ui.DocumentationPopupUI
import com.intellij.lang.documentation.ide.ui.DocumentationUI
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*

internal fun createDocumentationPopup(
  project: Project,
  browser: DocumentationBrowser,
  popupContext: PopupContext
): AbstractPopup {
  EDT.assertIsEdt()
  val popupUI = DocumentationPopupUI(project, DocumentationUI(project, browser))
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
  popupContext.setUpPopup(popup, popupUI)
  return popup
}

internal fun CoroutineScope.showPopupLater(popup: AbstractPopup, browser: DocumentationBrowser, popupContext: PopupContext) {
  EDT.assertIsEdt()
  val showJob = launch(ModalityState.current().asContextElement()) {
    // to avoid flickering: show popup immediately after the request is loaded OR after a timeout
    withTimeoutOrNull(DEFAULT_UI_RESPONSE_TIMEOUT) {
      browser.waitForContent()
    }
    withContext(Dispatchers.EDT) {
      check(!popup.isDisposed) // popup disposal should've cancelled this coroutine
      check(popup.canShow()) // sanity check
      popupContext.showPopup(popup)
    }
  }
  Disposer.register(popup, showJob::cancel)
}

internal fun resizePopup(popup: AbstractPopup) {
  popup.size = popup.component.preferredSize
}
