// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.layers.SwingIconLayer
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.impl.intellij.IntelliJIconManager
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Graphics
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.JLabel

@TestApplication
class SwingIconPaintTest {
  @BeforeEach
  fun activateIconManager() {
    IntelliJIconManager.activate()
  }

  @Test
  fun `an icon of no size paints nothing instead of failing the paint of its component`() {
    val icon = SwingIcon(iconDescriptor { })
    assertEquals(0, icon.iconWidth)
    assertEquals(0, icon.iconHeight)

    val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      // e.g. a toolbar: a failing icon would leave the rest of the toolbar unpainted
      icon.paintIcon(null, g, 0, 0)
      assertEquals(AffineTransform(), g.transform, "the graphics transform must be left as it was")
    }
    finally {
      g.dispose()
    }
  }

  @Test
  fun `a bridged legacy icon is painted for the component the icon is painted in`() {
    // a legacy icon (not an image) declaring the component non-null, as Kotlin icons often do
    val painted = mutableListOf<Component?>()
    val legacy = object : Icon {
      override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
        painted.add(c)
      }

      override fun getIconWidth() = 16

      override fun getIconHeight() = 16
    }
    val icon = SwingIcon(DefaultLayeredIconDescriptor(listOf(SwingIconLayer(legacy, IconModifier))))
    val component = JLabel()

    val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      icon.paintIcon(component, g, 0, 0)
    }
    finally {
      g.dispose()
    }

    assertEquals(1, painted.size, "the legacy icon must be painted once")
    assertSame(component, painted.single())
  }
}
