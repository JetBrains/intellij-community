// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.rendering

import com.intellij.ui.svg.SvgAttributePatcher
import com.intellij.util.SVGLoader
import org.jetbrains.icons.design.BlendMode
import org.jetbrains.icons.filters.ColorFilter
import org.jetbrains.icons.filters.TintColorFilter
import org.jetbrains.icons.patchers.SvgPatchOperation
import org.jetbrains.icons.patchers.SvgPatcher
import org.jetbrains.icons.impl.rendering.toAwtColor
import java.awt.Color
import java.awt.image.RGBImageFilter

internal fun ColorFilter.toAwtFilter(): RGBImageFilter {
  return when (this) {
    is TintColorFilter -> {
      AwtColorFilter.fromColorAndBlendMode(color, blendMode)
    }
  }
}

internal fun SvgPatcher.toIJPatcher(): SVGLoader.SvgElementColorPatcherProvider {
  return ProxySvgPatcher(this)
}

private class ProxySvgPatcher(
  private val patcher: SvgPatcher
): SVGLoader.SvgElementColorPatcherProvider, SvgAttributePatcher {
  override fun attributeForPath(path: String): SvgAttributePatcher = this
  override fun digest(): LongArray {
    return longArrayOf(patcher.hashCode().toLong())
  }

  override fun patchColors(attributes: MutableMap<String, String>) {
    // TODO Support filtered operations - not possible with current IJ svg loader
    for (operation in patcher.operations) {
      when (operation.operation) {
        SvgPatchOperation.Operation.Add -> {
          if (!attributes.containsKey(operation.attributeName)) {
            attributes[operation.attributeName] = operation.value!!
          }
        }
        SvgPatchOperation.Operation.Replace -> {
          if (operation.conditional) {
            val matches = attributes[operation.attributeName] == operation.expectedValue
            if (matches == !operation.negatedCondition) {
              attributes.replace(operation.attributeName, operation.value!!)
            }
          } else {
            attributes.replace(operation.attributeName, operation.value!!)
          }
        }
        SvgPatchOperation.Operation.Remove -> {
          if (operation.conditional) {
            val matches = attributes[operation.attributeName] == operation.expectedValue
            if (matches == !operation.negatedCondition) {
              attributes.remove(operation.attributeName)
            }
          } else {
            attributes.remove(operation.attributeName)
          }
        }
        SvgPatchOperation.Operation.Set -> attributes[operation.attributeName] = operation.value!!
      }
    }
  }
}

private class AwtColorFilter(val color: Color, val keepGray: Boolean, val keepBrightness: Boolean) : RGBImageFilter() {
  private val base = Color.RGBtoHSB(color.red, color.green, color.blue, null)

  override fun filterRGB(x: Int, y: Int, rgba: Int): Int {
    val r = rgba shr 16 and 0xff
    val g = rgba shr 8 and 0xff
    val b = rgba and 0xff
    val hsb = FloatArray(3)
    Color.RGBtoHSB(r, g, b, hsb)
    val rgb = Color.HSBtoRGB(base[0],
                             base[1] * if (keepGray) hsb[1] else 1.0f,
                             base[2] * if (keepBrightness) hsb[2] else 1.0f)
    return rgba and -0x1000000 or (rgb and 0xffffff)
  }

  companion object {
    fun fromColorAndBlendMode(color: org.jetbrains.icons.design.Color, blendMode: BlendMode): AwtColorFilter {
      var keepGray = true
      var keepBrightness = true
      when (blendMode) {
        BlendMode.Hue -> {
         keepGray = false
         keepBrightness = false
        }
        BlendMode.Saturation -> {
          keepGray = false
        }
        else -> {
          // Do nothing
        }
      }
      return AwtColorFilter(
        color.toAwtColor(),
        keepGray,
        keepBrightness
      )
    }
  }
}