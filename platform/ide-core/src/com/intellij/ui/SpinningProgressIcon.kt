// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.SVGLoader
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Color
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
open class SpinningProgressIcon: AnimatedIcon() {
  open val opacities
    get() = arrayOf(1.0, 0.875, 0.75, 0.625, 0.5, 0.375, 0.25, 0.125)

  open val paths
    get() = arrayOf("M8 2V4.5",
                    "M3.75739 3.75739L5.52515 5.52515",
                    "M2.0011 7.99738H4.5011",
                    "M3.75848 12.2401L5.52625 10.4723",
                    "M8.00214 13.998V11.498",
                    "M12.2414 12.2404L10.4736 10.4727",
                    "M13.9981 7.99921H11.4981",
                    "M12.2426 3.75739L10.4748 5.52515")
  open val size
    get() = 16

  private var iconColor: Color = JBColor.namedColor("ProgressIcon.color", JBColor(0x767A8A, 0xCED0D6))

  fun getCacheKey() = ColorUtil.toHex(iconColor)
  val iconCache = arrayOfNulls<Icon>(paths.size)
  var iconCacheKey = ""
  override fun createFrames() = Array(paths.size) { createFrame(it) }

  fun setIconColor(color: Color) {
    iconColor = color
  }

  fun getIconColor() = iconColor
  fun createFrame(i: Int): Frame {
    return object : Frame {
      override fun getIcon() = CashedDelegateIcon(i)
      override fun getDelay() = JBUI.getInt("ProgressIcon.delay", 125)
    }
  }
  inner class CashedDelegateIcon(val index: Int): Icon {
    private fun getDelegate() = getIconFromCache(index)
    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) = getDelegate().paintIcon(c, g, x, y)
    override fun getIconWidth() = getDelegate().iconWidth
    override fun getIconHeight() = getDelegate().iconHeight
  }
  private fun getIconFromCache(i: Int): Icon {
    val icon = iconCache[i]
    if (icon != null && iconCacheKey == getCacheKey()) {
      return icon
    }
    iconCache.forEachIndexed { index, _ ->
      run {
        val svg = generateSvgIcon(index)
        val scaleContext = ScaleContext.create()
        val image = SVGLoader.load(svg.byteInputStream(), scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat())
        iconCache[index] = IconUtil.toRetinaAwareIcon(image as BufferedImage)
      }
    }

    iconCacheKey = getCacheKey()
    return iconCache[i]!!
  }

  private fun generateSvgIcon(index: Int): String {
    val stroke = getCacheKey()
    var s = """<svg width="$size" height="$size" viewBox="0 0 $size $size" fill="none" xmlns="http://www.w3.org/2000/svg">""".plus("\n")
    for (n in paths.indices) {
      val opacity = opacities[(n + index) % opacities.size]
      s += """  <path opacity="$opacity" d="${paths[n]}" stroke="#$stroke" stroke-width="1.6" stroke-linecap="round"/>""".plus("\n")
    }
    s += "</svg>"
    return s
  }
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