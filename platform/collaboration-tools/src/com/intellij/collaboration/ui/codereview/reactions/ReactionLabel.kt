// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.reactions

import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.ui.hover.addHoverAndPressStateListener
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Cursor
import java.awt.event.*
import javax.swing.*

const val VARIATION_SELECTOR = "\uFE0F"

@Suppress("FunctionName")
fun ReactionLabel(
  layout: SizeRestrictedSingleComponentLayout,
  onClick: (component: JComponent) -> Unit = {},
  textFieldInitializer: JTextField.() -> Unit = {}
): JComponent {
  val emojiUnicodeLabel = JTextField().apply {
    isOpaque = false
    isEditable = false
    isFocusable = false
    highlighter = null
    border = JBUI.Borders.empty()
    horizontalAlignment = JTextField.CENTER

    getListeners(KeyListener::class.java).forEach(::removeKeyListener)
    getListeners(FocusListener::class.java).forEach(::removeFocusListener)
    getListeners(MouseListener::class.java).forEach(::removeMouseListener)
    getListeners(MouseMotionListener::class.java).forEach(::removeMouseMotionListener)
    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        with(parent) {
          e.source = this
          dispatchEvent(e)
        }
      }
    })

    textFieldInitializer()
  }

  return emojiUnicodeLabel.wrapInRoundedPanel(layout, onClick)
}

@Suppress("FunctionName")
fun ReactionLabel(
  layout: SizeRestrictedSingleComponentLayout,
  icon: Icon,
  onClick: (component: JComponent) -> Unit = {},
  labelInitializer: JLabel.() -> Unit = {}
): JComponent {
  return JLabel(icon, SwingConstants.CENTER)
    .apply(labelInitializer)
    .wrapInRoundedPanel(layout, onClick)
}

private fun JComponent.wrapInRoundedPanel(
  layout: SizeRestrictedSingleComponentLayout,
  onClick: (component: JComponent) -> Unit = {}
): JComponent {
  val component = this
  return RoundedPanel(layout, arc = CodeReviewReactionsUIUtil.BUTTON_ROUNDNESS).apply {
    UIUtil.setCursor(this, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
    addHoverAndPressStateListener(this, pressedStateCallback = { component, isPressed ->
      if (!isPressed) return@addHoverAndPressStateListener
      onClick(component as JComponent)
    })
    add(component)
  }
}