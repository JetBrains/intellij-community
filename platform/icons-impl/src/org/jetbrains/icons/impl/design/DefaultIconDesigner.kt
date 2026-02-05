// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.design

import org.jetbrains.icons.Icon
import org.jetbrains.icons.design.Shape
import org.jetbrains.icons.design.Color
import org.jetbrains.icons.design.IconAnimationDesigner
import org.jetbrains.icons.design.IconDesigner
import org.jetbrains.icons.design.IconUnit
import org.jetbrains.icons.modifiers.IconModifier
import org.jetbrains.icons.impl.DefaultLayeredIcon
import org.jetbrains.icons.impl.layers.AnimatedIconLayer
import org.jetbrains.icons.impl.layers.IconIconLayer
import org.jetbrains.icons.layers.IconLayer
import org.jetbrains.icons.impl.layers.ImageIconLayer
import org.jetbrains.icons.impl.layers.LayoutIconLayer
import org.jetbrains.icons.rendering.ImageResourceLoader
import org.jetbrains.icons.impl.layers.ShapeIconLayer

abstract class DefaultIconDesigner: IconDesigner {
  private val layers = mutableListOf<IconLayer>()

  protected fun image(loader: ImageResourceLoader, modifier: IconModifier) {
    layers.add(ImageIconLayer(loader, modifier))
  }

  override fun icon(icon: Icon, modifier: IconModifier) {
    layers.add(IconIconLayer(icon, modifier))
  }

  override fun custom(iconLayer: IconLayer) {
    layers.add(iconLayer)
  }

  override fun row(spacing: IconUnit, modifier: IconModifier, builder: IconDesigner.() -> Unit) {
    layout(LayoutIconLayer.LayoutDirection.Row, spacing, modifier, builder)
  }

  override fun column(spacing: IconUnit, modifier: IconModifier, builder: IconDesigner.() -> Unit) {
    layout(LayoutIconLayer.LayoutDirection.Column, spacing, modifier, builder)
  }

  private fun layout(direction: LayoutIconLayer.LayoutDirection, spacing: IconUnit, modifier: IconModifier, builder: IconDesigner.() -> Unit) {
    val nestedIconDesigner = createNestedDesigner()
    nestedIconDesigner.builder()
    layers.add(LayoutIconLayer(nestedIconDesigner.buildLayers(), direction, spacing, modifier))
  }

  override fun animation(modifier: IconModifier, builder: IconAnimationDesigner.() -> Unit) {
    val designer = DefaultIconAnimationDesigner(this)
    designer.builder()
    layers.add(AnimatedIconLayer(designer.build(), modifier))
  }

  override fun shape(color: Color, shape: Shape, modifier: IconModifier) {
    layers.add(ShapeIconLayer(color, shape, modifier))
  }

  abstract fun createNestedDesigner(): DefaultIconDesigner

  fun build(): DefaultLayeredIcon {
    return DefaultLayeredIcon(buildLayers())
  }

  fun buildLayers(): List<IconLayer> = layers.toList()
}