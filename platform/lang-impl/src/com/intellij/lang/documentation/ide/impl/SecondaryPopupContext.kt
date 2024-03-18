// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.impl

import com.intellij.lang.documentation.ide.ui.DocumentationPopupUI
import com.intellij.lang.documentation.ide.ui.PopupUpdateEvent
import com.intellij.openapi.ui.popup.ComponentPopupBuilder
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.documentation.impl.DocumentationRequest
import com.intellij.platform.backend.documentation.impl.EmptyDocumentationTarget
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

  protected abstract val closeOnClickOutside: Boolean

  override fun preparePopup(builder: ComponentPopupBuilder) {
    builder.setRequestFocus(false) // otherwise, it won't be possible to continue interacting with lookup/SE
    builder.setCancelOnClickOutside(closeOnClickOutside)
  }

  override fun setUpPopup(popup: AbstractPopup, popupUI: DocumentationPopupUI) {
    popupUI.updateFromRequests(requestFlow())
  }

  protected abstract fun requestFlow(): Flow<DocumentationRequest?>

  final override fun boundsHandler(): PopupBoundsHandler {
    return SecondaryPopupBoundsHandler(baseBoundsHandler())
  }

  protected abstract fun baseBoundsHandler(): PopupBoundsHandler
}

private class SecondaryPopupBoundsHandler(
  private val original: PopupBoundsHandler,
) : PopupBoundsHandler {

  override fun showPopup(popup: AbstractPopup) {
    original.showPopup(popup)
    // this is needed so that unfocused popup could still become focused
    popup.popupWindow.focusableWindowState = true
  }

  override suspend fun updatePopup(popup: AbstractPopup, resized: Boolean, popupUpdateEvent: PopupUpdateEvent) {
    original.updatePopup(popup, resized, popupUpdateEvent)
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

abstract class BaseAdjustingPopupBoundsHandler(
  private val referenceComponent: Component,
): PopupBoundsHandler {
  override fun showPopup(popup: AbstractPopup) {
    showPopup(popup, referenceComponent, popup.component.preferredSize)
    installPositionAdjuster(popup, referenceComponent) // move popup when reference component changes its width
  }

  override suspend fun updatePopup(popup: AbstractPopup, resized: Boolean, popupUpdateEvent: PopupUpdateEvent) {
    repositionPopup(popup, referenceComponent, popupSize(popup, resized).adjustForEvent(popup, popupUpdateEvent))
  }

  protected abstract fun componentResized(anchor: Component, popup: AbstractPopup)

  protected abstract fun popupSize(popup: AbstractPopup, resized: Boolean): Dimension

  protected fun repositionPopup(popup: AbstractPopup, anchor: Component, size: Dimension) {
    popup.setBounds(popupBounds(anchor, size))
  }

  protected abstract fun popupBounds(anchor: Component, size: Dimension): Rectangle

  private fun installPositionAdjuster(popup: AbstractPopup, anchor: Component) {
    val listener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        componentResized(anchor, popup)
      }
    }
    anchor.addComponentListener(listener)
    Disposer.register(popup) {
      anchor.removeComponentListener(listener)
    }
  }

  private fun showPopup(popup: AbstractPopup, anchor: Component, size: Dimension) {
    val bounds = popupBounds(anchor, size)
    popup.size = bounds.size
    popup.showInScreenCoordinates(anchor, bounds.location)
  }

}

class AdjusterPopupBoundsHandler(
  referenceComponent: Component,
) : BaseAdjustingPopupBoundsHandler(referenceComponent) {


  override fun componentResized(anchor: Component, popup: AbstractPopup) {
    repositionPopup(popup, anchor, popup.size)
  }

  override fun popupSize(popup: AbstractPopup, resized: Boolean): Dimension {
    return if (resized) {
      // popup was resized manually
      // => persisted size was restored
      popup.size
    }
    else {
      // popup was not resized manually
      // => no size was saved
      // => size should be computed by the popup content
      popup.component.preferredSize
    }
  }

  override fun popupBounds(anchor: Component, size: Dimension): Rectangle {
    return PositionAdjuster(anchor).adjustBounds(size, arrayOf(Position.RIGHT, Position.LEFT))
  }
}
