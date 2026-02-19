// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ui.icons.toRetinaAwareIcon
import com.intellij.ui.svg.loadSvg
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.util.function.Function
import javax.swing.Icon

/**
 * Internal utility class for generating loading icons on the fly.
 * Generated icons are UI theme-aware and can change base color using
 * <code>ProgressIcon.color</code> UI key.
 *
 * @see com.intellij.util.ui.AsyncProcessIcon
 * @see AnimatedIcon
 * @author Konstantin Bulenkov
 */
@Internal
class SpinningProgressIcon(
  private val paths: Array<String> = arrayOf(
    """ x="7"       y="1"       width="2" height="4" rx="1" """,
    """ x="2.34961" y="3.76416" width="2" height="4" rx="1" transform="rotate(-45 2.34961 3.76416)" """,
    """ x="1"       y="7"       width="4" height="2" rx="1" """,
    """ x="5.17871" y="9.40991" width="2" height="4" rx="1" transform="rotate(45 5.17871 9.40991)" """,
    """ x="7"       y="11"      width="2" height="4" rx="1" """,
    """ x="9.41016" y="10.8242" width="2" height="4" rx="1" transform="rotate(-45 9.41016 10.8242)" """,
    """ x="11"      y="7"       width="4" height="2" rx="1" """,
    """ x="12.2383" y="2.3501"  width="2" height="4" rx="1" transform="rotate(45 12.2383 2.3501)" """
  ),
  private val size: Int = 16,
) : AnimatedIcon(Function { Array(paths.size) { index -> (it as SpinningProgressIcon).createFrame(index) } }) {
  private val opacities: Array<Double>
    get() = arrayOf(1.0, 0.93, 0.78, 0.69, 0.62, 0.48, 0.38, 0.3)

  private var iconColor: Color = JBColor.namedColor("ProgressIcon.color", JBColor(0xA8ADBD, 0x6F737A))

  private val iconCache = arrayOfNulls<Icon>(paths.size)
  private var iconCacheKey = -1

  fun setIconColor(color: Color) {
    iconColor = color
  }

  fun getIconColor(): Color = iconColor

  private fun createFrame(i: Int): Frame {
    return object : Frame {
      override fun getIcon() = CashedDelegateIcon(i)
      override fun getDelay() = JBUI.getInt("ProgressIcon.delay", 125)
    }
  }

  private inner class CashedDelegateIcon(private val index: Int) : Icon {
    private fun getDelegate() = getIconFromCache(index)

    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
      getDelegate().paintIcon(c, g, x, y)
    }

    override fun getIconWidth(): Int = getDelegate().iconWidth

    override fun getIconHeight(): Int = getDelegate().iconHeight
  }

  private fun getIconFromCache(i: Int): Icon {
    val icon = iconCache[i]
    val cacheKey = iconColor.hashCode()
    if (icon != null && iconCacheKey == cacheKey) {
      return icon
    }

    val scale = JBUI.pixScale()
    val stringBuilder = StringBuilder()
    val header = createRooTag()
    val stroke = ColorUtil.toHex(iconColor)
    for (index in iconCache.indices) {
      stringBuilder.setLength(0)
      stringBuilder.append(header)
      generateSvgIcon(index = index, s = stringBuilder, stroke = stroke)
      iconCache[index] = toRetinaAwareIcon(loadSvg(data = stringBuilder.toString().encodeToByteArray(), scale = scale))
    }

    iconCacheKey = cacheKey
    return iconCache[i]!!
  }

  private fun createRooTag(): String {
    return """<svg width="$size" height="$size" viewBox="0 0 $size $size" fill="none" xmlns="http://www.w3.org/2000/svg">\n"""
  }

  @Suppress("unused")
  internal fun generateSvgIcon(index: Int): String {
    val s = StringBuilder(createRooTag())
    generateSvgIcon(index = index, s = s, stroke = ColorUtil.toHex(iconColor))
    return s.toString()
  }

  private fun generateSvgIcon(index: Int, s: StringBuilder, stroke: String) {
    for (n in paths.indices) {
      val opacity = opacities[(n + index) % opacities.size]
      s.append("""  <rect fill="#$stroke" opacity="$opacity" ${paths[n]} />""").append('\n')
    }
    s.append("</svg>")
  }
}

@Internal
fun bigSpinningProgressIcon(): SpinningProgressIcon {
  return SpinningProgressIcon(
    paths = arrayOf(
      """ x="14" y="2" width="4" height="8" rx="2" """,
      """ x="4.69922" y="7.52832" width="4" height="8" rx="2" transform="rotate(-45 4.69922 7.52832)" """,
      """ x="2" y="14" width="8" height="4" rx="2" """,
      """ x="10.35742" y="18.81982" width="4" height="8" rx="2" transform="rotate(45 10.35742 18.81982)" """,
      """ x="14" y="22" width="4" height="8" rx="2" """,
      """ x="18.82031" y="21.6484" width="4" height="8" rx="2" transform="rotate(-45 18.82031 21.6484)" """,
      """ x="22" y="14" width="8" height="4" rx="2" """,
      """ x="24.47656" y="4.7002" width="4" height="8" rx="2" transform="rotate(45 24.47656 4.7002)" """),
    size = 32,
  )
}

//fun main() {
//  val icon = SpinningProgressIcon()
//  val svgIcon = icon.generateSvgIcon(0)
//  println(svgIcon)
//  @Suppress("SSBasedInspection")
//  val tmpFile = File.createTempFile("krutilka", ".svg").also {
//    it.writeText(svgIcon)
//    it.deleteOnExit()
//  }
//  Desktop.getDesktop().open(tmpFile)
//}