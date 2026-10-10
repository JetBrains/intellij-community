// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.swing.createSwingIcon
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.UIManager
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

private const val DARK_THEME_KEY = "ui.theme.is.dark"

/** Renders the icon at [path] with [modifier] through the Swing path into a transparent ARGB image. */
internal fun render(path: String, modifier: IconModifier): BufferedImage {
  IntelliJIconManager.activate()
  return imageIconDescriptor(path, IntelliJIconManager::class.java.classLoader, modifier).createSwingIcon().paintToImage()
}

/** Paints the icon into a transparent ARGB image of the size of the icon. */
internal fun Icon.paintToImage(): BufferedImage {
  val image = BufferedImage(iconWidth, iconHeight, BufferedImage.TYPE_INT_ARGB)
  val g = image.createGraphics()
  try {
    paintIcon(null, g, 0, 0)
  } finally {
    g.dispose()
  }
  return image
}

/** Runs [action] in a dark theme. The renderer reads the theme flag when it chooses the artwork of an icon. */
internal fun <T> withDarkTheme(action: () -> T): T {
  val defaults = UIManager.getLookAndFeelDefaults()
  val previous = defaults.get(DARK_THEME_KEY)
  defaults.put(DARK_THEME_KEY, true)
  try {
    return action()
  } finally {
    defaults.put(DARK_THEME_KEY, previous)
  }
}

/** True when both images have the same size and the same pixels. */
internal fun sameImage(a: BufferedImage, b: BufferedImage): Boolean =
  a.width == b.width && a.height == b.height && a.argbPixels().contentEquals(b.argbPixels())

internal fun assertSameImage(expected: BufferedImage, actual: BufferedImage, message: String) {
  assertTrue(sameImage(expected, actual)) { message }
}

internal fun assertDifferentImage(a: BufferedImage, b: BufferedImage, message: String) {
  assertFalse(sameImage(a, b)) { message }
}

private fun BufferedImage.argbPixels(): IntArray = getRGB(0, 0, width, height, null, 0, width)
