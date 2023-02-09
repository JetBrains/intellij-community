// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.layout

import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBInsets
import java.awt.*
import kotlin.math.min

/**
 * Wraps a single component optionally limiting its size to [maxWidth] and [maxHeight]
 */
class SizeRestrictedSingleComponentLayout(
  var maxWidth: Int? = null,
  var maxHeight: Int? = null
) : LayoutManager2 {

  private var component: Component? = null

  override fun addLayoutComponent(comp: Component?, constraints: Any?) {
    component = comp
  }

  override fun addLayoutComponent(name: String?, comp: Component?) {
    component = comp
  }

  override fun removeLayoutComponent(comp: Component?) {
    if (comp == component) component = null
  }

  override fun minimumLayoutSize(parent: Container): Dimension =
    component?.takeIf { it.isVisible }?.minimumSize?.also {
      JBInsets.addTo(it, parent.insets)
    } ?: Dimension(0, 0)

  private fun getWidthRestriction(): Int = maxWidth?.let(JBUIScale::scale) ?: Int.MAX_VALUE
  private fun getHeightRestriction(): Int = maxHeight?.let(JBUIScale::scale) ?: Int.MAX_VALUE

  override fun preferredLayoutSize(parent: Container): Dimension {
    val prefSize = component?.takeIf { it.isVisible }?.preferredSize ?: return Dimension(0, 0)

    val prefWidth = min(prefSize.width, getWidthRestriction())
    val prefHeight = min(prefSize.height, getHeightRestriction())

    return Dimension(prefWidth, prefHeight).also {
      JBInsets.addTo(it, parent.insets)
    }
  }

  override fun maximumLayoutSize(target: Container): Dimension {
    val maxSize = component?.takeIf { it.isVisible }?.maximumSize ?: return Dimension(0, 0)

    val maxWidth = min(maxSize.width, getWidthRestriction())
    val maxHeight = min(maxSize.height, getHeightRestriction())

    return Dimension(maxWidth, maxHeight).also {
      JBInsets.addTo(it, target.insets)
    }
  }

  override fun layoutContainer(parent: Container) {
    if (component?.isVisible != true) return

    val bounds = Rectangle(0, 0, parent.width, parent.height)
    JBInsets.removeFrom(bounds, parent.insets)

    bounds.width = min(getWidthRestriction(), bounds.width)
    bounds.height = min(getHeightRestriction(), bounds.height)
    component?.bounds = bounds
  }

  override fun getLayoutAlignmentX(target: Container?): Float = 0f
  override fun getLayoutAlignmentY(target: Container?): Float = 0f
  override fun invalidateLayout(target: Container?) = Unit
}