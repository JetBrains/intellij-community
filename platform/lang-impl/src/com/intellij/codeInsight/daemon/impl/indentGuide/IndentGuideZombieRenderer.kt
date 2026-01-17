// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.indentGuide

import com.intellij.openapi.util.registry.Registry
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.Stroke


internal object IndentGuideZombieRenderer : IndentGuideRenderer() {

  private val ZOMBIE_DEBUG_INDENT_STROKE: Stroke = BasicStroke(
    /* width = */      1f,
    /* cap = */        BasicStroke.CAP_BUTT,
    /* join = */       BasicStroke.JOIN_BEVEL,
    /* miterlimit = */ 0f,
    /* dash = */       floatArrayOf(10f, 10f),
    /* dash_phase = */ 0f,
  )

  override fun drawLine(g: Graphics2D, x1: Int, y1: Int, x2: Int, y2: Int) {
    if (isZombieDebugEnabled()) {
      val prevStroke = g.stroke
      g.stroke = ZOMBIE_DEBUG_INDENT_STROKE
      g.drawLine(x1, y1, x2, y2)
      g.stroke = prevStroke
    } else {
      super.drawLine(g, x1, y1, x2, y2)
    }
  }

  private fun isZombieDebugEnabled(): Boolean {
    return Registry.`is`("cache.markup.debug", false)
  }
}
