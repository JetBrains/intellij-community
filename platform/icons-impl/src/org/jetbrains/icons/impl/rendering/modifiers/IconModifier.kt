// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering.modifiers

import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.InternalIconsApi
import org.jetbrains.icons.design.MaxIconUnit
import org.jetbrains.icons.design.DisplayPointIconUnit
import org.jetbrains.icons.design.IconUnit
import org.jetbrains.icons.design.IconVerticalAlign
import org.jetbrains.icons.design.IconHorizontalAlign
import org.jetbrains.icons.design.PercentIconUnit
import org.jetbrains.icons.design.PixelIconUnit
import org.jetbrains.icons.modifiers.AlignIconModifier
import org.jetbrains.icons.modifiers.AlphaIconModifier
import org.jetbrains.icons.modifiers.ColorFilterModifier
import org.jetbrains.icons.modifiers.CombinedIconModifier
import org.jetbrains.icons.modifiers.CutoutMarginModifier
import org.jetbrains.icons.modifiers.HeightIconModifier
import org.jetbrains.icons.modifiers.IconModifier
import org.jetbrains.icons.modifiers.MarginIconModifier
import org.jetbrains.icons.modifiers.StrokeModifier
import org.jetbrains.icons.modifiers.SvgPatcherModifier
import org.jetbrains.icons.modifiers.WidthIconModifier
import org.jetbrains.icons.rendering.Bounds
import org.jetbrains.icons.rendering.ScalingContext
import org.jetbrains.icons.impl.rendering.layers.LayerLayout
import org.jetbrains.icons.impl.rendering.layers.applyTo
import kotlin.math.roundToInt

@OptIn(ExperimentalIconsApi::class, InternalIconsApi::class)
fun IconModifier.applyTo(layout: LayerLayout, scaling: ScalingContext): LayerLayout {
  return when (this) {
    is CombinedIconModifier -> {
      other.applyTo(root.applyTo(layout, scaling), scaling)
    }
    is WidthIconModifier -> {
      layout.copy(
        layerBounds = layout.layerBounds.copy(
          width = width.asPixels(scaling, layout.parentBounds, true)
        )
      )
    }
    is HeightIconModifier -> {
      layout.copy(
        layerBounds = layout.layerBounds.copy(
          height = height.asPixels(scaling, layout.parentBounds, false)
        )
      )
    }
    is AlignIconModifier -> {
      val x = when (align.horizontalAlign) {
        IconHorizontalAlign.Left -> {
          layout.layerBounds.x
        }
        IconHorizontalAlign.Right -> {
          layout.parentBounds.width - layout.layerBounds.width
        }
        IconHorizontalAlign.Center -> {
          (layout.parentBounds.width / 2) - (layout.layerBounds.width / 2)
        }
      }
      val y = when (align.verticalAlign) {
        IconVerticalAlign.Top -> {
          layout.layerBounds.y
        }
        IconVerticalAlign.Bottom -> {
          layout.parentBounds.height - layout.layerBounds.height
        }
        IconVerticalAlign.Center -> {
          (layout.parentBounds.height / 2) - (layout.layerBounds.height / 2)
        }
      }
      layout.copy(
        layerBounds = layout.layerBounds.copy(
          x = x,
          y = y
        )
      )
    }
    is MarginIconModifier -> applyMarginIconModifier(this, layout, scaling)
    is AlphaIconModifier -> layout.copy(alpha = alpha)
    is ColorFilterModifier -> layout // applyColorFilterModifier(this, layout, displayScale) // ImageModifier
    is SvgPatcherModifier -> layout // ImageModifier
    is CutoutMarginModifier -> applyCutoutMarginModifier(this, layout, scaling)
    is StrokeModifier -> layout
    IconModifier.Companion -> layout
  }
}

fun applyStrokeModifier(modifier: StrokeModifier, layout: LayerLayout): LayerLayout {
  return layout.copy(stroke = modifier.color)
}

fun applyCutoutMarginModifier(modifier: CutoutMarginModifier, layout: LayerLayout, scaling: ScalingContext): LayerLayout {
  val final = layout.calculateFinalBounds()
  val size = modifier.size.asFractionalPixels(scaling, final, true)
  return layout.copy(cutoutMargin = size)
}

fun IconUnit.asFractionalPixels(scaling: ScalingContext, bounds: Bounds, isWidth: Boolean = false): Float {
  return when (this) {
    is PixelIconUnit -> value
    is PercentIconUnit -> if (isWidth) {
      bounds.width * value
    } else {
      bounds.height * value
    }
    is DisplayPointIconUnit -> scaling.applyTo(value)
    is MaxIconUnit -> {
      if (isWidth) {
        bounds.width
      } else {
        bounds.height
      }
    }
  }.toFloat()
}

fun IconUnit.asPixels(scaling: ScalingContext, bounds: Bounds, isWidth: Boolean = false): Int {
  return asFractionalPixels(scaling, bounds, isWidth).roundToInt()
}