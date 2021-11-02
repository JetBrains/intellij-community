// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.impl

import com.intellij.lang.documentation.ide.ui.DocumentationPopupUI
import com.intellij.lang.documentation.impl.DocumentationRequest
import com.intellij.lang.documentation.impl.EmptyDocumentationTarget
import com.intellij.openapi.ui.popup.ComponentPopupBuilder
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.popup.PopupPositionManager.Position
import com.intellij.ui.popup.PopupPositionManager.PositionAdjuster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

internal abstract class SecondaryPopupContext : PopupContext {

  abstract val referenceComponent: Component

  override fun preparePopup(builder: ComponentPopupBuilder) {
    builder.setRequestFocus(false) // otherwise, it won't be possible to continue interacting with lookup/SE
    builder.setCancelOnClickOutside(false) // otherwise, selecting lookup items by mouse, or resizing SE would close the popup
  }

  override fun setUpPopup(popup: AbstractPopup, popupUI: DocumentationPopupUI) {
    val resized = popupUI.useStoredSize()
    if (!resized.get()) {
      // a popup might be shown before its content was loaded (with "Fetching..." message)
      // => no update events were generated => ensure popup size is set
      resizePopup(popup)
    }
    popupUI.updatePopup {
      if (!resized.get()) {
        resizePopup(popup)
      }
      // don't reposition the popup, it sticks to the reference component
    }
    popupUI.updateFromRequests(requestFlow())
  }

  abstract fun requestFlow(): Flow<DocumentationRequest?>

  override fun showPopup(popup: AbstractPopup) {
    val component = referenceComponent
    repositionPopup(popup, component) // also shows the popup
    installPositionAdjuster(popup, component) // move popup when reference component changes its width
    // this is needed so that unfocused popup could still become focused
    popup.popupWindow.focusableWindowState = true
  }
}

private fun DocumentationPopupUI.updateFromRequests(requests: Flow<DocumentationRequest?>) {
  coroutineScope.updateFromRequests(requests, browser)
}

private fun CoroutineScope.updateFromRequests(requests: Flow<DocumentationRequest?>, browser: DocumentationBrowser) {
  launch(Dispatchers.Default) {
    requests.collectLatest {
      val request = it ?: EmptyDocumentationTarget.request
      browser.resetBrowser(request)
    }
  }
}

private fun installPositionAdjuster(popup: JBPopup, anchor: Component) {
  val listener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent) {
      repositionPopup(popup, anchor)
    }
  }
  anchor.addComponentListener(listener)
  Disposer.register(popup) {
    anchor.removeComponentListener(listener)
  }
}

private fun repositionPopup(popup: JBPopup, anchor: Component) {
  PositionAdjuster(anchor).adjust(popup, popup.size, Position.RIGHT, Position.LEFT)
}
