// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.ui.svg.SvgAttributePatcher
import com.intellij.util.SVGLoader
import com.intellij.platform.icons.design.BlendMode
import com.intellij.platform.icons.filters.ColorFilter
import com.intellij.platform.icons.impl.filters.TintColorFilter
import com.intellij.platform.icons.impl.patchers.AUTHORED_STROKE_VARIANT_SUFFIX
import com.intellij.platform.icons.impl.patchers.DefaultSvgPatcher
import com.intellij.platform.icons.impl.patchers.SvgPatchOperation
import com.intellij.platform.icons.impl.patchers.authoredStrokeSvgPatcher
import com.intellij.platform.icons.impl.patchers.strokeSvgPatcher
import com.intellij.platform.icons.impl.patchers.writeSvgAttribute
import com.intellij.platform.icons.swing.toAwtColor
import java.awt.Color
import java.awt.image.RGBImageFilter
import com.intellij.platform.icons.design.Color as DesignColor

internal fun ColorFilter.toAwtFilter(): RGBImageFilter {
  return when (this) {
    is TintColorFilter -> {
      AwtColorFilter.fromColorAndBlendMode(color, blendMode)
    }
    else -> error("Unsupported color filter: $this")
  }
}

/**
 * The color patcher this frontend hands to the IntelliJ SVG loader for an icon stroked in [stroke] and patched by
 * [patcher], on top of whatever [rootPatcher] already patches.
 *
 * Returns `null` when there is nothing of our own to patch, which is what tells the loader to take its plain path.
 */
internal fun toIJPatcher(
  stroke: DesignColor?,
  patcher: DefaultSvgPatcher?,
  rootPatcher: SVGLoader.SvgElementColorPatcherProvider?,
): SVGLoader.SvgElementColorPatcherProvider? {
  if (stroke == null && patcher == null) return null
  return ProxySvgPatcher(stroke = stroke, patcher = patcher, rootPatcher = rootPatcher)
}

private class ProxySvgPatcher(
  private val stroke: DesignColor?,
  private val patcher: DefaultSvgPatcher?,
  private val rootPatcher: SVGLoader.SvgElementColorPatcherProvider? = null
): SVGLoader.SvgElementColorPatcherProvider {
  // Which stroke patch applies depends on the file the loader ends up resolving, so it can only be picked per path: a
  // hand-authored stroke variant is recolored as it is, while a base icon is reduced to an outline.
  override fun attributeForPath(path: String): SvgAttributePatcher {
    val strokePatcher = stroke?.let {
      if (path.isAuthoredStrokeVariant()) authoredStrokeSvgPatcher(it) else strokeSvgPatcher(it)
    }
    // The icon's own patcher runs first and the stroke substitution after it, so an icon that explicitly recolors a
    // palette color keeps that color: the stroke operation no longer matches what the explicit one already replaced.
    val combined = patcher?.combineWith(strokePatcher) ?: strokePatcher
    return ProxySvgAttributePatcher(combined as? DefaultSvgPatcher, rootPatcher?.attributeForPath(path))
  }

  override fun digest(): LongArray {
    val own = longArrayOf(patcher.hashCode().toLong(), stroke.hashCode().toLong())
    if (rootPatcher != null) {
      return rootPatcher.digest() + own
    } else {
      return own
    }
  }
}

private fun String.isAuthoredStrokeVariant(): Boolean =
  substringBeforeLast('.', missingDelimiterValue = "").endsWith(AUTHORED_STROKE_VARIANT_SUFFIX)

private class ProxySvgAttributePatcher(
  private val patcher: DefaultSvgPatcher?,
  private val rootPatcher: SvgAttributePatcher? = null
): SvgAttributePatcher {
  override fun patchColors(attributes: MutableMap<String, String>) {
    rootPatcher?.patchColors(attributes)
    // TODO Support filtered operations - not possible with current IJ svg loader
    val write = { name: String, value: String ->
      writeSvgAttribute(name, value, { n, v -> attributes[n] = v }, { attributes.remove(it) })
    }
    for (operation in patcher?.operations ?: return) {
      when (operation.operation) {
        SvgPatchOperation.Operation.Add -> {
          if (!attributes.containsKey(operation.attributeName)) {
            write(operation.attributeName, operation.value!!)
          }
        }
        SvgPatchOperation.Operation.Replace -> {
          // Replace never creates an attribute, conditionally or not: an element that does not carry the attribute
          // inherits it, and adding one here would override that inheritance. Add exists for that.
          if (attributes.containsKey(operation.attributeName) &&
              (!operation.conditional ||
               operation.matches(attributes[operation.attributeName]) != operation.negatedCondition)
          ) {
            write(operation.attributeName, operation.value!!)
          }
        }
        SvgPatchOperation.Operation.Remove -> {
          if (operation.conditional) {
            if (operation.matches(attributes[operation.attributeName]) != operation.negatedCondition) {
              attributes.remove(operation.attributeName)
            }
          } else {
            attributes.remove(operation.attributeName)
          }
        }
        SvgPatchOperation.Operation.Set -> write(operation.attributeName, operation.value!!)
      }
    }
  }
}

private class AwtColorFilter(color: Color, val keepGray: Boolean, val keepBrightness: Boolean) : RGBImageFilter() {
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
    fun fromColorAndBlendMode(color: DesignColor, blendMode: BlendMode): AwtColorFilter {
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