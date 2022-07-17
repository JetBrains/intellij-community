package com.intellij.codeInsight.codeVision.ui.renderers.painters

import com.intellij.openapi.editor.Editor
import com.intellij.util.IconUtil
import java.awt.AlphaComposite
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import javax.swing.Icon
import kotlin.math.roundToInt

class CodeVisionScaledIconPainter(val yShiftIconMultiplier: Double = 0.865, val scaleMultiplier: Double = 0.8) : ICodeVisionPainter {

  fun paint(editor: Editor, g: Graphics, icon: Icon, point: Point, scaleFactor: Float) {
    val scaledIcon = IconUtil.scale(icon, editor.component, scaleFactor)
    val g2d = g as Graphics2D
    val composite = g2d.composite
    g2d.composite = AlphaComposite.SrcOver
    scaledIcon.paintIcon(editor.component, g, point.x, point.y - (yShiftIconMultiplier * scaledIcon.iconHeight).toInt())
    g2d.composite = composite
  }

  fun scaleFactor(iconValue: Int, neededValue: Int) = (neededValue * scaleMultiplier).toFloat() / iconValue
  fun width(icon: Icon, scaleFactor: Float): Int = (icon.iconWidth * scaleFactor).toDouble().roundToInt()
  fun height(icon: Icon, scaleFactor: Float): Int = (icon.iconHeight * scaleFactor).toDouble().roundToInt()

}