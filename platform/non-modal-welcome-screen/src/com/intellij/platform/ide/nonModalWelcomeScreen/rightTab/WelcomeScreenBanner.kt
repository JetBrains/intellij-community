// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.InlineBanner
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil.drawImage
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Image
import java.awt.Rectangle

@ApiStatus.Internal
open class WelcomeScreenBanner(
  private val imageLight: () -> Image,
  private val imageDark: () -> Image,
  private val preferredHeight: (Int) -> Int,
) : WelcomeScreenBannerComponent() {
  override fun fillBanner(g: Graphics) {
    val image = if (JBColor.isBright()) imageLight() else imageDark()
    val dst = Rectangle(width, height)
    val src = Rectangle(image.getWidth(this), image.getHeight(this))
    drawImage(g, image, dst, src, null, null)
  }

  var preferredWidth: (Int) -> Int = { it }

  override fun getPreferredSize(): Dimension {
    val preferredSize = super.getPreferredSize()
    return Dimension(preferredWidth(preferredSize.width), preferredHeight(preferredSize.height))
  }
}

@ApiStatus.Internal
open class WelcomeScreenBannerComponent : InlineBanner(status = EditorNotificationPanel.Status.NeutralMuted) {
  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    val newWidth = JBUI.scale(380)
    if (newWidth < width) {
      super.setBounds(x + (width - newWidth) / 2, y, newWidth, height)
    }
    else {
      super.setBounds(x, y, width, height)
    }
  }

  fun setContent(title: String, description: String) {
    @Suppress("HardCodedStringLiteral")
    setMessage("<html><body><span style=\"font-size:${JBFont.labelFontSize() + 3}pt;font-weight:600;\">$title</span><br><br>$description</body></html>")
  }
}
