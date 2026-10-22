// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.icons.pathTransform
import java.awt.image.BufferedImage
import javax.swing.Icon

fun <T> withDarkIcons(dark: Boolean = true, action: () -> T): T {
  val previous = pathTransform.get().isDark
  IconLoader.setUseDarkIcons(dark)
  try {
    return action()
  }
  finally {
    IconLoader.setUseDarkIcons(previous)
  }
}

fun Icon.paintToImage(): BufferedImage {
  val image = BufferedImage(iconWidth, iconHeight, BufferedImage.TYPE_INT_ARGB)
  val g = image.createGraphics()
  try {
    paintIcon(null, g, 0, 0)
  }
  finally {
    g.dispose()
  }
  return image
}

fun sameImage(a: BufferedImage, b: BufferedImage): Boolean {
  if (a.width != b.width || a.height != b.height) return false
  for (y in 0 until a.height) {
    for (x in 0 until a.width) {
      if (a.getRGB(x, y) != b.getRGB(x, y)) return false
    }
  }
  return true
}
