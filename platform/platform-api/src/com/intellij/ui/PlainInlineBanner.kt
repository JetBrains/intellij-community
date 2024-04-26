// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBSwingUtilities
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.Graphics
import java.awt.Graphics2D

@Internal
class PlainInlineBanner(
  messageText: @Nls String = "",
  status: EditorNotificationPanel.Status = EditorNotificationPanel.Status.Info,
): InlineBanner(messageText, status) {

  override fun getComponentGraphics(g: Graphics?): Graphics {
    val g2 = super.getComponentGraphics(g)
    (g2 as? Graphics2D)?.setRenderingHint(JBSwingUtilities.ADJUSTED_BACKGROUND_ALPHA, 0.55f)
    return g2
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val config = GraphicsUtil.setupAAPainting(g)
    g.color = background
    g.fillRect(0, 0, width, height)
    config.restore()
  }
}