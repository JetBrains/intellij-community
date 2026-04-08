// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.render

import com.intellij.ide.structureView.StructureViewTreeElement
import java.awt.Color
import java.awt.geom.Rectangle2D

data class MinimapRenderEntry(
    val element: StructureViewTreeElement?,
    val rect2d: Rectangle2D.Double,
    val color: Color? = null,
    val sampleOffset: Int = NO_SAMPLE_OFFSET,
) {
  fun isSameEntry(other: MinimapRenderEntry?): Boolean = element === other?.element

  companion object {
    const val NO_SAMPLE_OFFSET: Int = -1
  }
}
