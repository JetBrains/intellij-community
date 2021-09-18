// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.lang.documentation.ide.ui.DocumentationPopupUI
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.popup.PopupPositionManager
import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

internal class LookupPopupContext(val lookup: LookupEx) : PopupContext {

  override fun setUpPopup(popup: AbstractPopup, popupUI: DocumentationPopupUI) {
    popupUI.coroutineScope.updateFromLookup(popupUI.browser, lookup)
    val resized = popupUI.useStoredSize()
    popupUI.updatePopup {
      if (!resized.get()) {
        resizePopup(popup)
      }
      // don't reposition the popup, it sticks to the lookup
    }
    cancelPopupWhenLookupIsClosed(lookup, popup)
  }

  override fun showPopup(popup: AbstractPopup) {
    val lookupComponent = lookup.component
    repositionPopup(popup, lookupComponent) // also shows the popup
    installPositionAdjuster(popup, lookupComponent) // move popup when lookup changes its width
    // this is needed so that unfocused popup could still become focused
    popup.popupWindow.focusableWindowState = true
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
  PopupPositionManager.PositionAdjuster(anchor).adjust(popup, PopupPositionManager.Position.RIGHT, PopupPositionManager.Position.LEFT)
}

private fun cancelPopupWhenLookupIsClosed(lookup: Lookup, popup: AbstractPopup) {
  lookup.addLookupListener(object : LookupListener {

    override fun itemSelected(event: LookupEvent): Unit = lookupClosed()

    override fun lookupCanceled(event: LookupEvent): Unit = lookupClosed()

    private fun lookupClosed() {
      lookup.removeLookupListener(this)
      popup.cancel()
    }
  })
}
