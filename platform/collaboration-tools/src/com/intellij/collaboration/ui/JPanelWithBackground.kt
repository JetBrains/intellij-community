// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import java.awt.Graphics
import java.awt.LayoutManager
import javax.swing.JPanel

/**
 * Panel which paints its background independently from [isOpaque] property
 *
 * WARN: do not set background with alpha when [isOpaque] is true
 */
open class JPanelWithBackground : JPanel {
  @Suppress("unused")
  constructor() : super()

  @Suppress("unused")
  constructor(layout: LayoutManager?, isDoubleBuffered: Boolean) : super(layout, isDoubleBuffered)

  @Suppress("unused")
  constructor(layout: LayoutManager?) : super(layout)

  @Suppress("unused")
  constructor(isDoubleBuffered: Boolean) : super(isDoubleBuffered)

  override fun paintComponent(g: Graphics) {
    // opaque background is painted in javax.swing.plaf.ComponentUI.update
    if (!isOpaque && isBackgroundSet) {
      g.color = background
      g.fillRect(0, 0, width, height)
    }
    super.paintComponent(g)
  }
}