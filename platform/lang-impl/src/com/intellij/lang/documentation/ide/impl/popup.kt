// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.lang.documentation.ide.ui.DocumentationPopupUI
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun createDocumentationPopup(
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
  popupContext.setUpPopup(popup, popupUI)
  return popup
}

internal fun CoroutineScope.showPopupLater(
  popup: AbstractPopup,
  popupUI: DocumentationPopupUI,
  boundsHandler: PopupBoundsHandler,
) {
  EDT.assertIsEdt()
  val resized = popupUI.useStoredSize()
  popupUI.updatePopup {
    boundsHandler.updatePopup(popup, resized.get())
  }
  val showJob = launch(ModalityState.current().asContextElement()) {
    // to avoid flickering: show popup after the UI has anything to show
    popupUI.ui.waitForContentUpdate()
    withContext(Dispatchers.EDT) {
      check(!popup.isDisposed) // popup disposal should've cancelled this coroutine
      check(popup.canShow()) // sanity check
      boundsHandler.showPopup(popup)
    }
  }
  Disposer.register(popup, showJob::cancel)
}

internal fun resizePopup(popup: AbstractPopup) {
  popup.size = popup.component.preferredSize
}
