/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.ui

import com.intellij.util.ui.AbstractLayoutManager
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import javax.swing.JComponent

/*
Based on [java.awt.GridLayout], but with support for extra width for option buttons.
 */
class DialogWrapperButtonLayout(val size: Int, val hgap: Int) : AbstractLayoutManager() {
  override fun layoutContainer(parent: Container) {
    synchronized(parent.treeLock) {
      val insets = parent.insets
      val totalExtraWidth = getTotalExtraWidth(parent)
      val totalGapsWidth = (size - 1) * hgap
      val widthWOInsets = parent.width - (insets.left + insets.right)
      val widthOnComponent = (widthWOInsets - totalGapsWidth - totalExtraWidth) / size

      val heightWOInsets = parent.height - (insets.top + insets.bottom)

      var x = insets.left + (widthWOInsets - (widthOnComponent * size + totalGapsWidth + totalExtraWidth)) / 2
      val y = insets.top
      for (comp in parent.components) {
        val w = widthOnComponent + getExtraWidth(comp)

        comp.setBounds(x, y, w, heightWOInsets)
        x += w + hgap
      }
    }
  }

  override fun preferredLayoutSize(parent: Container) = layoutSize(parent) { it.preferredSize }

  override fun minimumLayoutSize(parent: Container) = layoutSize(parent) { it.minimumSize }

  private fun layoutSize(parent: Container, sizeProvider: (Component) -> Dimension): Dimension {
    synchronized(parent.treeLock) {
      val insets = parent.insets
      var w = 0
      var h = 0
      for (comp in parent.components) {
        val d = sizeProvider(comp)

        w = maxOf(w, d.width)
        h = maxOf(h, d.height)
      }
      return Dimension(insets.left + insets.right + size * w + (size - 1) * hgap + getTotalExtraWidth(parent),
                       insets.top + insets.bottom + h)
    }
  }

  private fun getTotalExtraWidth(parent: Container) = parent.components.sumBy { getExtraWidth(it) }
  private fun getExtraWidth(comp: Component) = (comp as? JComponent)?.getClientProperty(EXTRA_WIDTH_KEY) as? Int ?: 0

  companion object {
    const val EXTRA_WIDTH_KEY = "ExtraWidth"
  }
}