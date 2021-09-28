// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.documentation.DocumentationManager.NEW_JAVADOC_LOCATION_AND_SIZE
import com.intellij.codeWithMe.ClientId
import com.intellij.lang.documentation.ide.ui.DEFAULT_UI_RESPONSE_TIMEOUT
import com.intellij.lang.documentation.ide.ui.DocumentationPopupUI
import com.intellij.lang.documentation.ide.ui.DocumentationUI
import com.intellij.lang.documentation.impl.DocumentationRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.DimensionService
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

internal fun showDocumentationPopup(project: Project, request: DocumentationRequest, popupContext: PopupContext): AbstractPopup {
  EDT.assertIsEdt()

  val (browser, browseJob) = DocumentationBrowser.createBrowserAndGetJob(project, initialRequest = request)
  val popupUI = DocumentationPopupUI(project, DocumentationUI(project, browser))
  val popup = createDocumentationPopup(project, popupUI, popupContext)
  popupUI.setPopup(popup)
  popupContext.setUpPopup(popup, popupUI)
  popupUI.coroutineScope.showPopupLater(popup, browseJob, popupContext)
  return popup
}

private fun createDocumentationPopup(
  project: Project,
  popupUI: DocumentationPopupUI,
  popupContext: PopupContext,
): AbstractPopup {
  return JBPopupFactory.getInstance()
    .createComponentPopupBuilder(popupUI.component, popupUI.preferableFocusComponent)
    .setProject(project)
    .addUserData(ClientId.current)
    .setResizable(true)
    .setMovable(true)
    .setFocusable(true)
    .setRequestFocus(popupContext !is LookupPopupContext) // otherwise, it won't be possible to interact with completion
    .setCancelOnClickOutside(popupContext !is LookupPopupContext) // otherwise, selecting lookup items by mouse would close the popup
    .setModalContext(false)
    .createPopup() as AbstractPopup
}

private fun CoroutineScope.showPopupLater(popup: AbstractPopup, browseJob: Job, popupContext: PopupContext) {
  launch {
    // to avoid flickering: show popup immediately after the request is loaded OR after a timeout
    select<Unit> {
      browseJob.onJoin {}
      launch { delay(DEFAULT_UI_RESPONSE_TIMEOUT) }.onJoin {}
    }
    withContext(Dispatchers.EDT) {
      check(!popup.isDisposed)
      check(popup.canShow())
      popupContext.showPopup(popup)
    }
  }
}

internal fun resizePopup(popup: AbstractPopup) {
  popup.size = popup.component.preferredSize
}

fun storeSize(project: Project, popup: AbstractPopup, parent: Disposable) {
  val resizeState = PopupResizeState(project, popup)
  popup.addResizeListener(resizeState, parent)
  val storedSize = DimensionService.getInstance().getSize(NEW_JAVADOC_LOCATION_AND_SIZE, project)
  if (storedSize != null) {
    resizeState.manuallyResized = true
    popup.size = storedSize
  }
}

private class PopupResizeState(
  private val project: Project,
  private val popup: AbstractPopup,
) : Runnable {

  var manuallyResized = false

  override fun run() {
    manuallyResized = true
    DimensionService.getInstance().setSize(NEW_JAVADOC_LOCATION_AND_SIZE, popup.contentSize, project)
  }
}
