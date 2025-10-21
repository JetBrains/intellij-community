// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.ui.JBColor
import com.intellij.ui.scale.ScaleType
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBScalableIcon
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics
import java.lang.ref.WeakReference

class TextIcon(val text: String, component: Component, val fontSize: Float) : JBScalableIcon() {
  private var font: Font? = null
  private var metrics: FontMetrics? = null
  private val componentRef = WeakReference(component)

  var highlight: Boolean = true

  init {
    isIconPreScaled = false
    scaleContext.addUpdateListener { update() }
    update()
  }

  // x,y is in USR_SCALE
  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    val customG = g.create()
    try {
      GraphicsUtil.setupAntialiasing(customG)
      customG.font = font
      val strX = scaleVal(x.toDouble(), ScaleType.OBJ_SCALE).toInt() + scaleVal(2.0).toInt()
      val strY = scaleVal(y.toDouble(), ScaleType.OBJ_SCALE).toInt() + iconHeight - metrics!!.descent - scaleVal(1.0).toInt()
      if (highlight) {
        UIUtil.drawStringWithHighlighting(customG,
                                          this.text,
                                          strX,
                                          strY,
                                          JBColor.foreground(),
                                          JBColor.background())
      } else {
        customG.color = JBColor.foreground()
        customG.drawString(this.text, strX, strY)
      }
    }
    finally {
      customG.dispose()
    }
  }

  override fun getIconWidth(): Int = metrics!!.stringWidth(this.text) + scaleVal(4.0).toInt()

  override fun getIconHeight(): Int = metrics!!.height

  private fun update() {
    // fontSize is in USR_SCALE
    font = JBFont.create(JBFont.label().deriveFont(scaleVal(fontSize.toDouble(), ScaleType.OBJ_SCALE).toFloat()))
    metrics = (componentRef.get() ?: object : Component() {}).getFontMetrics(font)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TextIcon) return false
    if (this.text != other.text) return false
    return font == other.font
  }

  override fun hashCode(): Int {
    var result = text.hashCode()
    result = 31 * result + fontSize.hashCode()
    result = 31 * result + (font?.hashCode() ?: 0)
    result = 31 * result + (metrics?.hashCode() ?: 0)
    result = 31 * result + componentRef.hashCode()
    return result
  }
}