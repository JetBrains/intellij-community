// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.ui.InlineBanner
import com.intellij.ui.JBColor
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
