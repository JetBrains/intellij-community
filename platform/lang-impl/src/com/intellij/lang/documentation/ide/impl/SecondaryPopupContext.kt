// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.lang.documentation.ide.ui.DocumentationPopupUI
import com.intellij.lang.documentation.impl.DocumentationRequest
import com.intellij.lang.documentation.impl.EmptyDocumentationTarget
import com.intellij.openapi.ui.popup.ComponentPopupBuilder
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
import java.awt.Dimension
import java.awt.Rectangle
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
    popupUI.updatePopup {
      val newSize = if (resized.get()) popup.size else popup.component.preferredSize
      repositionPopup(popup, referenceComponent, newSize)
    }
    popupUI.updateFromRequests(requestFlow())
  }

  abstract fun requestFlow(): Flow<DocumentationRequest?>

  override fun showPopup(popup: AbstractPopup) {
    val component = referenceComponent
    showPopup(popup, component)
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

private fun installPositionAdjuster(popup: AbstractPopup, anchor: Component) {
  val listener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent) {
      repositionPopup(popup, anchor, popup.size)
    }
  }
  anchor.addComponentListener(listener)
  Disposer.register(popup) {
    anchor.removeComponentListener(listener)
  }
}

private fun showPopup(popup: AbstractPopup, anchor: Component) {
  val bounds = popupBounds(anchor, popup.size)
  popup.size = bounds.size
  popup.showInScreenCoordinates(anchor, bounds.location)
}

private fun repositionPopup(popup: AbstractPopup, anchor: Component, size: Dimension) {
  popup.setBounds(popupBounds(anchor, size))
}

private fun popupBounds(anchor: Component, size: Dimension): Rectangle {
  return PositionAdjuster(anchor).adjustBounds(size, arrayOf(Position.RIGHT, Position.LEFT))
}
