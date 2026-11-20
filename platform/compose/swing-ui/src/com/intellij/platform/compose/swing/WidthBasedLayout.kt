// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose.swing

import com.intellij.ui.WidthBasedLayout
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Constraints
import org.jetbrains.compose.swing.foundation.layout.IntrinsicMeasurable
import org.jetbrains.compose.swing.foundation.layout.IntrinsicMeasureScope
import org.jetbrains.compose.swing.foundation.layout.LayoutModifierNode
import org.jetbrains.compose.swing.foundation.layout.LayoutModifierNodeElement
import org.jetbrains.compose.swing.foundation.layout.Measurable
import org.jetbrains.compose.swing.foundation.layout.MeasureResult
import org.jetbrains.compose.swing.foundation.layout.MeasureScope
import org.jetbrains.compose.swing.foundation.layout.constrainHeight
import org.jetbrains.compose.swing.foundation.layout.constrainWidth
import org.jetbrains.compose.swing.modifier.SwingModifier

/**
 * Measures a component with its [WidthBasedLayout] sizes.
 * The component must implement [WidthBasedLayout] and answer size queries without changing its bounds.
 */
@ApiStatus.Experimental
context(scope: ConstrainedScope)
public fun SwingModifier.widthBasedLayout(): SwingModifier = with(scope) { layout(WidthBasedLayoutElement) }

private object WidthBasedLayoutElement : LayoutModifierNodeElement<WidthBasedLayoutNode>() {
  override val name: String get() = "widthBasedLayout"
  override val declaredValues: Map<String, Any?> get() = emptyMap()

  override fun create(): WidthBasedLayoutNode = WidthBasedLayoutNode()

  override fun update(node: WidthBasedLayoutNode) {}

  override fun equals(other: Any?): Boolean = this === other

  override fun hashCode(): Int = javaClass.hashCode()
}

private class WidthBasedLayoutNode : LayoutModifierNode() {
  private val sizes: WidthBasedLayout
    get() = component as? WidthBasedLayout
            ?: error("widthBasedLayout() needs a component that implements WidthBasedLayout, but ${component.javaClass.name} " +
                     "does not. Remove the modifier: a Foundation container sizes a stock component to its granted width.")

  override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
    val width = constraints.constrainWidth(sizes.preferredWidth)
    val height = constraints.constrainHeight(sizes.getPreferredHeight(width))
    val placeable = measurable.measure(Constraints.fixed(width, height))
    return layout(width, height) { placeable.place(0, 0) }
  }

  override fun IntrinsicMeasureScope.minIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int {
    sizes
    return measurable.minIntrinsicWidth(height)
  }

  override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurable: IntrinsicMeasurable, height: Int): Int =
    sizes.preferredWidth

  override fun IntrinsicMeasureScope.minIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
    sizes.getPreferredHeight(width)

  override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurable: IntrinsicMeasurable, width: Int): Int =
    sizes.getPreferredHeight(width)
}