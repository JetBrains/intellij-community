// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.comment

import com.intellij.ui.IdeBorderFactory
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.geom.RoundRectangle2D
import javax.swing.JPanel
import kotlin.properties.Delegates.observable

@ApiStatus.Internal
class RoundedPanel(layout: LayoutManager?, private val arc: Int = 8) : JPanel(layout) {

  init {
    cursor = Cursor.getDefaultCursor()
    border = IdeBorderFactory.createRoundedBorder(arc + 2)
  }

  override fun paintChildren(g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {
      g2.clip(getShape())
      super.paintChildren(g2)
    }
    finally {
      g2.dispose()
    }
  }

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {
      GraphicsUtil.setupRoundedBorderAntialiasing(g2)
      g2.clip(getShape())
      super.paintComponent(g2)
    }
    finally {
      g2.dispose()
    }
  }

  private fun getShape(): Shape {
    val rect = Rectangle(size)
    JBInsets.removeFrom(rect, insets)
    // 2.25 scale is a @#$!% so we adjust sizes manually
    return RoundRectangle2D.Float(rect.x.toFloat() - 0.5f, rect.y.toFloat() - 0.5f,
                                  rect.width.toFloat()  + 0.5f, rect.height.toFloat() + 0.5f,
                                  arc.toFloat(), arc.toFloat())
  }
}
