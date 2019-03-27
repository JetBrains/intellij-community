// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation

import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Graphics2D
import java.awt.image.BufferedImage

/**
 * Draws image. If you need to position image inside inlay, use [InsetPresentation]
 */
class ImagePresentation(val image: BufferedImage) : BasePresentation() {
  override val width: Int
    get() = image.width
  override val height: Int
    get() = image.height

  override fun paint(g: Graphics2D, attributes: TextAttributes) {
    g.drawImage(image, 0, 0) { img, infoflags, x, y, width, height -> false }
  }

  override fun toString(): String = "<image>"
}