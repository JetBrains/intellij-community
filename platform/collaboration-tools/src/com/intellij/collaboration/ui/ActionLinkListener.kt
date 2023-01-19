// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import com.intellij.ide.BrowserUtil
import com.intellij.ui.HyperlinkAdapter
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.event.HyperlinkEvent

class ActionLinkListener(
  private val component: JComponent,
) : HyperlinkAdapter() {
  var action: Action? = null

  init {
    component.registerKeyboardAction(
      ActionListener { action?.actionPerformed(it) },
      KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
      JComponent.WHEN_FOCUSED
    )
  }

  override fun hyperlinkActivated(event: HyperlinkEvent) {
    if (event.description == ERROR_ACTION_HREF) {
      val actionEvent = ActionEvent(component, ActionEvent.ACTION_PERFORMED, ACTION_EVENT_LINK_COMMAND)
      action?.actionPerformed(actionEvent)
    }
    else {
      BrowserUtil.browse(event.description)
    }
  }

  companion object {
    private const val ACTION_EVENT_LINK_COMMAND = "perform"
    const val ERROR_ACTION_HREF = "ERROR_ACTION"
  }
}