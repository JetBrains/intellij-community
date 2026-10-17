// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.scale.IconScale
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [SwingIcon] is the `javax.swing.Icon` that renders an [IconDescriptor] through the new icons API. When it ends up as a
 * component's icon, the UI Inspector (`com.intellij.internal.inspector.components.ValueCellRenderer`) prints it through
 * `Icon.toString()` to fill the icon cell's "path" text. These tests pin that the wrapper surfaces the descriptor - and
 * therefore its layers and image paths - instead of the default `SwingIcon@hash`.
 *
 * The descriptor and scale are read directly, so no icon manager or rendering machinery is needed here.
 */
class SwingIconToStringTest {
  private class FakeDescriptor : IconDescriptor {
    override fun toString(): String = "DefaultIcon(layers=[ImageIconLayer(path='expui/general/copy.svg')])"
  }

  @Test
  fun `toString exposes the descriptor so the inspector can show layers and paths`() {
    val text = SwingIcon(FakeDescriptor()).toString()
    assertTrue("expui/general/copy.svg" in text, "the descriptor's image path must be visible, was: $text")
    assertTrue("ImageIconLayer" in text, "the descriptor's layers must be visible, was: $text")
  }

  @Test
  fun `toString includes the scale when the icon is scaled`() {
    val scale = object : IconScale {
      override fun toFactor(density: Float, originalWidth: Int, originalHeight: Int, contextScale: Float): Float = 2f
      override fun toString(): String = "x2"
    }
    val text = SwingIcon(FakeDescriptor(), scale).toString()
    assertTrue("expui/general/copy.svg" in text, "the descriptor must still be visible when scaled, was: $text")
    assertTrue("x2" in text, "the scale must be visible, was: $text")
  }
}
