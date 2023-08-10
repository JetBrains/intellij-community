// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ui

import java.awt.KeyboardFocusManager
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JComponent

/**
 * Checks if the key event is a [KeyEvent.VK_TAB] or [shift][KeyEvent.isShiftDown] + [KeyEvent.VK_TAB] event,
 * consumes the event if so,
 * and moves the focus to next/previous component after/before the containing [NewNavBarPanel].
 */
internal class NavBarItemComponentTabKeyListener(private val panel: JComponent) : KeyAdapter() {

  override fun keyPressed(e: KeyEvent) {
    if (e.keyCode == KeyEvent.VK_TAB && e.source is NavBarItemComponent) {
      e.consume()
      jumpToNextComponent(!e.isShiftDown)
    }
  }

  private fun jumpToNextComponent(next: Boolean) {
    // The base will be first or last NavBarItemComponent in the NewNavBarPanel
    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    if (next) {
      focusManager.focusNextComponent(panel.getComponent(panel.componentCount - 1))
    }
    else {
      focusManager.focusPreviousComponent(panel.getComponent(0))
    }
  }
}
