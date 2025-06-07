// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Graphics
import javax.swing.JEditorPane
import javax.swing.JPanel
import kotlin.math.max

abstract class InlineBannerBase(
  status: EditorNotificationPanel.Status,
  gap: Int,
  messageText: @Nls String,
) : JBPanel<InlineBannerBase>() {

  open var status: EditorNotificationPanel.Status = status
    set(value) {
      field = value
      background = value.background

      revalidate()
      repaint()
    }

  protected var messageText: @Nls String
    get() = message.text
    set(value) {
      message.text = value
      if (message.caret != null) {
        message.caretPosition = 0
      }
    }

  protected val iconPanel: JPanel = JPanel(BorderLayout())
  protected val centerPanel: JPanel = JPanel(VerticalLayout(gap))
  protected val message: JEditorPane = JEditorPane()

  init {
    border = JBUI.Borders.empty(12)
    isOpaque = false
    background = status.background

    iconPanel.isOpaque = isOpaque
    iconPanel.background = background

    centerPanel.isOpaque = isOpaque
    centerPanel.background = background

    message.isOpaque = isOpaque
    message.background = background
    message.border = null
    message.isEditable = false
    message.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, java.lang.Boolean.TRUE)
    message.contentType = UIUtil.HTML_MIME
    message.editorKit = HTMLEditorKitBuilder().build()
    message.addHyperlinkListener(BrowserHyperlinkListener())

    this.messageText = messageText
  }

  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    super.setBounds(x, y, max(width, JBUI.scale(256)), height)
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val config = GraphicsUtil.setupAAPainting(g)
    val cornerRadius = JBUI.scale(16)
    g.color = background
    g.fillRoundRect(0, 0, width, height, cornerRadius, cornerRadius)
    g.color = status.border
    g.drawRoundRect(0, 0, width - 1, height - 1, cornerRadius, cornerRadius)
    config.restore()
  }
}