// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.ui.InlineBanner
import com.intellij.ui.JBColor
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil.drawImage
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Image
import java.awt.Rectangle

@ApiStatus.Internal
open class WelcomeScreenBanner(private val imageLight: () -> Image, private val imageDark: () -> Image) : InlineBanner() {
  override fun fillBanner(g: Graphics) {
    val image = if (JBColor.isBright()) imageLight() else imageDark()
    val dst = Rectangle(width, height)
    val src = Rectangle(image.getWidth(this), image.getHeight(this))
    drawImage(g, image, dst, src, null, null)
  }
}

@ApiStatus.Internal
open class WelcomeScreenBannerComponent : InlineBanner() {
  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    val newWidth = JBUI.scale(380)
    if (newWidth < width) {
      super.setBounds(x + (width - newWidth) / 2, y, newWidth, height)
    }
    else {
      super.setBounds(x, y, width, height)
    }
  }

  override fun fillBanner(g: Graphics) {
    val config = GraphicsUtil.setupAAPainting(g)
    val cornerRadius = JBUI.scale(16)
    g.color = JBColor.namedColor("WelcomeTab.Banner.infoBackground", JBUI.CurrentTheme.Banner.INFO_BACKGROUND)
    g.fillRoundRect(0, 0, width, height, cornerRadius, cornerRadius)
    g.color = JBColor.namedColor("WelcomeTab.Banner.infoBorderColor", JBUI.CurrentTheme.Banner.INFO_BORDER_COLOR)
    g.drawRoundRect(0, 0, width - 1, height - 1, cornerRadius, cornerRadius)
    config.restore()
  }
}