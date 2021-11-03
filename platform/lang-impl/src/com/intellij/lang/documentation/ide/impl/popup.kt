// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.documentation.DocumentationManager.NEW_JAVADOC_LOCATION_AND_SIZE
import com.intellij.codeWithMe.ClientId
import com.intellij.lang.documentation.ide.ui.DEFAULT_UI_RESPONSE_TIMEOUT
import com.intellij.lang.documentation.ide.ui.DocumentationPopupUI
import com.intellij.lang.documentation.ide.ui.DocumentationUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.DimensionService
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

internal fun CoroutineScope.showPopupLater(popup: AbstractPopup, browseJob: Job, popupContext: PopupContext) {
  EDT.assertIsEdt()
  val showJob = launch(ModalityState.current().asContextElement()) {
    browseJob.tryJoin() // to avoid flickering: show popup immediately after the request is loaded OR after a timeout
    withContext(Dispatchers.EDT) {
      check(!popup.isDisposed) // popup disposal should've cancelled this coroutine
      check(popup.canShow()) // sanity check
      popupContext.showPopup(popup)
    }
  }
  Disposer.register(popup, showJob::cancel)
}

/**
 * Suspends until the job is done, or timeout is exceeded.
 */
private suspend fun Job.tryJoin() {
  withTimeoutOrNull(DEFAULT_UI_RESPONSE_TIMEOUT) {
    this@tryJoin.join()
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
