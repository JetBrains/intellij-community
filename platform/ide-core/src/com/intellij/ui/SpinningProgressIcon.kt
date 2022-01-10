// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.SVGLoader
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Component
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.Icon

/**
 * Internal utility class for generating loading icons on the fly.
 * Generated icons are UI theme aware and can change base color using
 * <code>ProgressIcon.color</code> UI key.
 *
 * @see com.intellij.util.ui.AsyncProcessIcon
 * @see AnimatedIcon
 * @author Konstantin Bulenkov
 */
@Internal
class SpinningProgressIcon: AnimatedIcon(*createFrames())

val opacities = arrayOf(1.0, 0.915, 0.83, 0.745, 0.66, 0.575, 0.49, 0.405, 0.32, 0.235, 0.15, 0.065)
val paths = arrayOf("M8 2V4",
                    "M5 2.80383L6 4.53588",
                    "M2.80396 5L4.53601 6",
                    "M2.00024 8L4.00024 8",
                    "M2.80396 11L4.53601 10",
                    "M5.00024 13.1962L6.00024 11.4641",
                    "M8.00024 14L8.00024 12",
                    "M11.0002 13.1962L10.0002 11.4641",
                    "M13.1965 11L11.4645 10",
                    "M14.0002 8L12.0002 8",
                    "M13.1965 5L11.4645 6",
                    "M11.0002 2.80383L10.0002 4.53588")
val iconCache = arrayOfNulls<Icon>(paths.size)
var iconCacheKey = ""

fun currentCacheKey() = ColorUtil.toHex(getIconColor())

fun getIconColor() = JBColor.namedColor("ProgressIcon.color", JBColor(0x767A8A, 0xCED0D6))

fun createFrames() = Array(paths.size) { createFrame(it) }

fun createFrame(i: Int): AnimatedIcon.Frame {
  return object : AnimatedIcon.Frame {
    override fun getIcon() = CashedDelegateIcon(i)
    override fun getDelay() = Integer.getInteger("ProgressIcon.delay", 100)
  }
}

class CashedDelegateIcon(val index: Int): Icon {
  private fun getDelegate() = getIconFromCache(index)
  override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) = getDelegate().paintIcon(c, g, x, y)
  override fun getIconWidth() = getDelegate().iconWidth
  override fun getIconHeight() = getDelegate().iconHeight
}

private fun getIconFromCache(i: Int): Icon {
  val icon = iconCache[i]
  if (icon != null && iconCacheKey == currentCacheKey()) {
    return icon
  }
  iconCache.fill(null)
  val svg = generateSvgIcon(i)
  val scaleContext = ScaleContext.create()
  val image = SVGLoader.load(svg.byteInputStream(), scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat())

  iconCache[i] = IconUtil.toRetinaAwareIcon(image as BufferedImage)
  iconCacheKey = currentCacheKey()
  return iconCache[i]!!
}

private fun generateSvgIcon(index: Int): String {
  val stroke = currentCacheKey()
  var s = """<svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">""".plus("\n")
  for (n in paths.indices) {
    val opacity = opacities[(n + index) % opacities.size]
    s += """  <path opacity="$opacity" d="${paths[n]}" stroke="#$stroke" stroke-linecap="round"/>""".plus("\n")
  }
  s += "</svg>"
  return s
}

//fun main() {
//  val svgIcon = generateSvgIcon(0)
//  println(svgIcon)
//  @Suppress("SSBasedInspection")
//  val tmpFile = File.createTempFile("krutilka", ".svg").also {
//    it.writeText(svgIcon)
//    it.deleteOnExit()
//  }
//  Desktop.getDesktop().open(tmpFile)
//}