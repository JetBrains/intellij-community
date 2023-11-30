// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.layout

import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.util.ui.JBInsets
import java.awt.*
import kotlin.math.min

/**
 * Wraps a single component limiting its size to [maxSize] aor overriding the preferred size with [prefSize]
 */
class SizeRestrictedSingleComponentLayout : LayoutManager2 {

  var maxSize: DimensionRestrictions = DimensionRestrictions.None
  var prefSize: DimensionRestrictions = DimensionRestrictions.None

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
    component?.takeIf { it.isVisible }?.minimumSize?.let {
      maxSize.limitMax(it)
    }?.also {
      JBInsets.addTo(it, parent.insets)
    } ?: Dimension(0, 0)

  override fun preferredLayoutSize(parent: Container): Dimension =
    component?.takeIf { it.isVisible }?.preferredSize?.let {
      it.width = prefSize.getWidth() ?: it.width
      it.height = prefSize.getHeight() ?: it.height
      maxSize.limitMax(it)
    }?.also {
      JBInsets.addTo(it, parent.insets)
    } ?: Dimension(0, 0)

  override fun maximumLayoutSize(target: Container): Dimension =
    component?.takeIf { it.isVisible }?.maximumSize?.let {
      maxSize.limitMax(it)
    }?.also {
      JBInsets.addTo(it, target.insets)
    } ?: Dimension(0, 0)

  override fun layoutContainer(parent: Container) {
    if (component?.isVisible != true) return

    component?.bounds = Rectangle(0, 0, parent.width, parent.height).apply {
      JBInsets.removeFrom(this, parent.insets)
      size = maxSize.limitMax(size)
    }
  }

  override fun getLayoutAlignmentX(target: Container?): Float = 0f
  override fun getLayoutAlignmentY(target: Container?): Float = 0f
  override fun invalidateLayout(target: Container?) = Unit //TODO: cache

  companion object {
    fun constant(maxWidth: Int? = null, maxHeight: Int? = null): SizeRestrictedSingleComponentLayout =
      SizeRestrictedSingleComponentLayout().apply {
        maxSize = DimensionRestrictions.ScalingConstant(maxWidth, maxHeight)
      }
  }
}

private fun DimensionRestrictions.limitMax(size: Dimension): Dimension {
  val width = min(size.width, getWidth() ?: Int.MAX_VALUE)
  val height = min(size.height, getHeight() ?: Int.MAX_VALUE)
  return Dimension(width, height)
}