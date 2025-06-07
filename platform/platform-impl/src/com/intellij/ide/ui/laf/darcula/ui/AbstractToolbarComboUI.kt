// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.ide.ui.laf.darcula.ui

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.impl.AbstractToolbarCombo
import com.intellij.ui.JBColor
import com.intellij.ui.util.maximumWidth
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import sun.swing.SwingUtilities2
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Rectangle
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.*
import javax.swing.plaf.ComponentUI
import javax.swing.plaf.basic.BasicHTML
import javax.swing.text.BadLocationException
import javax.swing.text.View
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
open class AbstractToolbarComboUI : ComponentUI(), PropertyChangeListener {

  protected open fun setUIDefaults(c: AbstractToolbarCombo) {
    c.setForeground(JBColor.namedColor("MainToolbar.Dropdown.foreground", JBColor.foreground()))
    c.setBackground(JBColor.namedColor("MainToolbar.Dropdown.background", JBColor.background()))
    val hoverBG = JBColor.namedColor("MainToolbar.Dropdown.hoverBackground", JBColor.background())
    c.hoverBackground = hoverBG
    c.transparentHoverBackground = JBColor.namedColor("MainToolbar.Dropdown.transparentHoverBackground", hoverBG)

    val maxWidth = UIManager.getInt("MainToolbar.Dropdown.maxWidth")
    if (maxWidth > 0) c.maximumWidth = maxWidth
  }

  override fun propertyChange(evt: PropertyChangeEvent) {
    if (evt.propertyName == "text" || evt.propertyName == "font") {
      val widget = evt.source as AbstractToolbarCombo
      tryUpdateHtmlRenderer(widget, widget.text)
    }
  }

  protected fun tryUpdateHtmlRenderer(widget: AbstractToolbarCombo, text: String?) {
    if (widget.font == null && BasicHTML.isHTMLString(text)) {
      return
    }
    BasicHTML.updateRenderer(widget, text)
  }

  protected fun drawText(c: AbstractToolbarCombo, fullText: String, g: Graphics2D, textRect: Rectangle) {
    val metrics = c.getFontMetrics(c.font)
    g.color = if (c.isEnabled) c.getForeground() else UIUtil.getLabelDisabledForeground()

    val baseline = c.getBaseline(c.width, c.height)
    val text = c.textCutStrategy.calcShownText(fullText, metrics, textRect.width, c)
    val strBounds = metrics.getStringBounds(text, g).getBounds()
    strBounds.setLocation((textRect.centerX - strBounds.centerX).toInt().coerceAtLeast(textRect.x), baseline)

    val v = c.getClientProperty(BasicHTML.propertyKey) as? View
    if (v != null) {
      strBounds.y -= metrics.ascent
      v.paint(g, strBounds)
    }
    else {
      SwingUtilities2.drawString(c, g, text, strBounds.x, strBounds.y)
    }
  }

  protected fun paintIcons(icons: List<Icon>, combo: AbstractToolbarCombo, g: Graphics2D, rect: Rectangle): Rectangle {
    if (icons.isEmpty()) return Rectangle()

    val bounds = Rectangle(rect.x, rect.y, rect.width, rect.height)

    var maxHeight = 0
    var minY = Int.MAX_VALUE
    var width = 0
    for (icon in icons) {
      if (width != 0) width += combo.betweenIconsGap
      val x: Int = bounds.x + width
      val y: Int = bounds.y + bounds.height / 2 - icon.iconHeight / 2
      icon.paintIcon(combo, g, x, y)
      width += icon.iconWidth
      maxHeight = max(maxHeight, icon.iconHeight)
      minY = min(minY, y)
    }

    return Rectangle(rect.x, minY, width, maxHeight)
  }

  protected fun Rectangle.cutLeft(delta: Int) {
    x += delta
    width -= delta
  }

  override fun getBaseline(c: JComponent, width: Int, height: Int): Int {
    super.getBaseline(c, width, height)
    val widget = c as AbstractToolbarCombo
    val iconRect = Rectangle()
    val textRect = Rectangle()
    SwingUtilities.layoutCompoundLabel(c, c.getFontMetrics(c.getFont()), getText(widget), null,
                                       SwingConstants.CENTER, SwingConstants.CENTER, SwingConstants.CENTER, SwingConstants.CENTER,
                                       Rectangle(width, height), iconRect, textRect, 0)
    val fm = c.getFontMetrics(c.getFont())
    return textRect.y + fm.ascent
  }

  @Nls
  protected fun getText(widget: AbstractToolbarCombo): String? {
    val v = widget.getClientProperty(BasicHTML.propertyKey) as? View
    if (v != null) {
      try {
        val text: @NlsSafe String? = v.document.getText(0, v.document.length).trim()
        return text
      }
      catch (ignored: BadLocationException) {
      }
    }
    return widget.text
  }

  protected fun calcIconsWidth(icons: List<Icon>, gapBetweenIcons: Int): Int {
    var res = 0
    for (icon in icons) {
      if (res > 0) res += gapBetweenIcons
      res += icon.iconWidth
    }
    return res
  }

  override fun getMinimumSize(c: JComponent): Dimension {
    if (c !is AbstractToolbarCombo) return Dimension()
    val preferredSize = c.preferredSize
    val metrics = c.getFontMetrics(c.getFont())
    val currentTextWidth = UIUtil.computeStringWidth(c, c.text ?: "")
    val minTextWidth = c.textCutStrategy.calcMinTextWidth(c.text ?: "", metrics, c)
    return Dimension(preferredSize.width - currentTextWidth + minTextWidth, preferredSize.height)
  }
}