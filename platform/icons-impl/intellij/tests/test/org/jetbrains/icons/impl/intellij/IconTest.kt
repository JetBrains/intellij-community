// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij

import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.icons.imageIcon
import org.jetbrains.icons.swing.toSwingIcon
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

@TestApplication
class IconTest {
  @Test
  fun `should render icon to buffered image`() {
    IntelliJIconManager.activate()
    val icon = imageIcon("actions/addFile.svg", IconTest::class.java.classLoader)

    val swingIcon = icon.toSwingIcon()
    val image = BufferedImage(swingIcon.iconWidth, swingIcon.iconHeight, BufferedImage.TYPE_INT_ARGB)
    val g2 = image.createGraphics()
    try {
      swingIcon.paintIcon(null, g2, 0, 0)
    } finally {
      g2.dispose()
    }
  }
}
