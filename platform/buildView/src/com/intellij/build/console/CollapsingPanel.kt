// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.console

import com.intellij.openapi.observable.properties.AtomicProperty
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.JPanel

internal class CollapsingPanel(
  private val preferredLines: Int,
  private val preferredLinesThreshold: Int,
  private val content: Component,
) : JPanel(BorderLayout()) {

  enum class CollapseState { COLLAPSED, EXPANDED, NOT_NEEDED }

  val collapseStateProperty = AtomicProperty(CollapseState.NOT_NEEDED)

  private val fontHeight: Int get() = content.getFontMetrics(content.font).height
  private val preferredHeight: Int get() = fontHeight * preferredLines
  private val preferredHeightThreshold: Int get() = fontHeight * preferredLinesThreshold

  init {
    isOpaque = false
    add(content, BorderLayout.CENTER)
    if (isCollapseNeeded()) {
      collapseStateProperty.set(CollapseState.COLLAPSED)
    }

    collapseStateProperty.afterChange {
      revalidate()
      repaint()
    }
  }

  override fun getPreferredSize(): Dimension {
    val preferred = super.getPreferredSize()
    return when (collapseStateProperty.get()) {
      CollapseState.EXPANDED, CollapseState.NOT_NEEDED -> preferred
      CollapseState.COLLAPSED -> Dimension(preferred.width, minOf(preferred.height, preferredHeight))
    }
  }

  override fun doLayout() {
    collapseStateProperty.updateAndGet { state ->
      when {
        !isCollapseNeeded() -> CollapseState.NOT_NEEDED
        state == CollapseState.NOT_NEEDED -> CollapseState.COLLAPSED
        else -> state
      }
    }
    super.doLayout()
  }

  private fun isCollapseNeeded(): Boolean = content.preferredSize.height > preferredHeightThreshold
}
