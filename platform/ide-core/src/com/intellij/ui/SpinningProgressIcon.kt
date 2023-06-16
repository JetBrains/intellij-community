// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ui.icons.toRetinaAwareIcon
import com.intellij.ui.scale.DerivedScaleType
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.svg.loadSvg
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
open class SpinningProgressIcon : AnimatedIcon() {
  open val opacities: Array<Double>
    get() = arrayOf(1.0, 0.93, 0.78, 0.69, 0.62, 0.48, 0.38, 0.3)

  open val paths: Array<String>
    get() = arrayOf(""" x="7"       y="1"       width="2" height="4" rx="1" """,
                    """ x="2.34961" y="3.76416" width="2" height="4" rx="1" transform="rotate(-45 2.34961 3.76416)" """,
                    """ x="1"       y="7"       width="4" height="2" rx="1" """,
                    """ x="5.17871" y="9.40991" width="2" height="4" rx="1" transform="rotate(45 5.17871 9.40991)" """,
                    """ x="7"       y="11"      width="2" height="4" rx="1" """,
                    """ x="9.41016" y="10.8242" width="2" height="4" rx="1" transform="rotate(-45 9.41016 10.8242)" """,
                    """ x="11"      y="7"       width="4" height="2" rx="1" """,
                    """ x="12.2383" y="2.3501"  width="2" height="4" rx="1" transform="rotate(45 12.2383 2.3501)" """)
  open val size: Int
    get() = 16

  private var iconColor: Color = JBColor.namedColor("ProgressIcon.color", JBColor(0xA8ADBD, 0x6F737A))

  fun getCacheKey(): String = ColorUtil.toHex(iconColor)

  private val iconCache = arrayOfNulls<Icon>(paths.size)
  private var iconCacheKey = ""

  override fun createFrames(): Array<Frame> = Array(paths.size) { createFrame(it) }

  fun setIconColor(color: Color) {
    iconColor = color
  }

  fun getIconColor(): Color = iconColor
  fun createFrame(i: Int): Frame {
    return object : Frame {
      override fun getIcon() = CashedDelegateIcon(i)
      override fun getDelay() = JBUI.getInt("ProgressIcon.delay", 125)
    }
  }

  inner class CashedDelegateIcon(val index: Int) : Icon {
    private fun getDelegate() = getIconFromCache(index)
    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int): Unit = getDelegate().paintIcon(c, g, x, y)
    override fun getIconWidth(): Int = getDelegate().iconWidth
    override fun getIconHeight(): Int = getDelegate().iconHeight
  }

  private fun getIconFromCache(i: Int): Icon {
    val icon = iconCache[i]
    if (icon != null && iconCacheKey == getCacheKey()) {
      return icon
    }

    val scaleContext = ScaleContext.create()
    val scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat()
    for ((index, _) in iconCache.withIndex()) {
      val svg = generateSvgIcon(index)
      val image = loadSvg(data = svg, scale = scale)
      iconCache[index] = toRetinaAwareIcon(image as BufferedImage)
    }

    iconCacheKey = getCacheKey()
    return iconCache[i]!!
  }

  private fun generateSvgIcon(index: Int): ByteArray {
    val stroke = getCacheKey()
    val s = StringBuilder("""<svg width="$size" height="$size" viewBox="0 0 $size $size" fill="none" xmlns="http://www.w3.org/2000/svg">""").append('\n')
    for (n in paths.indices) {
      val opacity = opacities[(n + index) % opacities.size]
      s.append("""  <rect fill="#$stroke" opacity="$opacity" ${paths[n]} />""").append('\n')
    }
    s.append("</svg>")
    return s.toString().encodeToByteArray()
  }

  class Big: SpinningProgressIcon() {
    override val paths: Array<String>
      get() = arrayOf(""" x="14" y="2" width="4" height="8" rx="2" """,
                      """ x="4.69922" y="7.52832" width="4" height="8" rx="2" transform="rotate(-45 4.69922 7.52832)" """,
                      """ x="2" y="14" width="8" height="4" rx="2" """,
                      """ x="10.35742" y="18.81982" width="4" height="8" rx="2" transform="rotate(45 10.35742 18.81982)" """,
                      """ x="14" y="22" width="4" height="8" rx="2" """,
                      """ x="18.82031" y="21.6484" width="4" height="8" rx="2" transform="rotate(-45 18.82031 21.6484)" """,
                      """ x="22" y="14" width="8" height="4" rx="2" """,
                      """ x="24.47656" y="4.7002" width="4" height="8" rx="2" transform="rotate(45 24.47656 4.7002)" """)

    override val size: Int
      get() = 32
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