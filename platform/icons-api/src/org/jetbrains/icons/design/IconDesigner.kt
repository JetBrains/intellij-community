// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.design

import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.Icon
import org.jetbrains.icons.layers.IconLayer
import org.jetbrains.icons.modifiers.IconModifier
import org.jetbrains.icons.modifiers.align
import org.jetbrains.icons.modifiers.cutoutMargin
import org.jetbrains.icons.modifiers.size

/**
 * Individual methods act as layers and the order dictates in which order they are rendered.
 *
 * @param IconModifier Modifications that should be performed on the Layer, like sizing, margin, color filters etc. (order-dependant)
 */
@ExperimentalIconsApi
interface IconDesigner {
  fun image(path: String, classLoader: ClassLoader? = null, modifier: IconModifier = IconModifier)
  fun icon(icon: Icon, modifier: IconModifier = IconModifier)
  fun row(spacing: IconUnit = 0.px, modifier: IconModifier = IconModifier, builder: IconDesigner.() -> Unit)
  fun column(spacing: IconUnit = 0.px, modifier: IconModifier = IconModifier, builder: IconDesigner.() -> Unit)
  fun animation(modifier: IconModifier = IconModifier, builder: IconAnimationDesigner.() -> Unit)
  fun shape(color: Color, shape: Shape, modifier: IconModifier)
  /**
   * Adds custom layer type to this designer, keep in mind that additional registration of serializers/renderers is needed
   * for the layer to be used. Check the specific Icon Manager used to see registration details.
   */
  fun custom(iconLayer: IconLayer)
}

fun IconDesigner.badge(
  color: Color,
  shape: Shape,
  size: IconUnit = (3.5 * 2).dp relativeTo 20.dp,
  align: IconAlign = IconAlign.TopRight,
  cutout: IconUnit = 1.5.dp relativeTo 20.dp,
  modifier: IconModifier = IconModifier,
) {
  shape(color, shape, modifier.size(size).align(align).cutoutMargin(cutout))
}

@ExperimentalIconsApi
interface IconAnimationDesigner {
  fun frame(duration: Long, builder: IconDesigner.() -> Unit)
}