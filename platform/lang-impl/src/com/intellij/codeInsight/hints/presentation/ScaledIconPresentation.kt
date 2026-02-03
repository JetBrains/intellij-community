// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation

import com.intellij.codeInsight.hints.fireContentChanged
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.IconUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.Graphics2D
import javax.swing.Icon
import kotlin.math.ceil

/**
 * Draws image. If you need to position image inside inlay, use [InsetPresentation]
 */
@ApiStatus.Internal
open class ScaledIconPresentation(
  private val metricsStorage: InlayTextMetricsStorage,
  val isSmall: Boolean,
  icon: Icon, private val component: Component) : BasePresentation() {
  var icon: Icon = icon
  set(value) {
    field = value
    fireContentChanged()
  }

  private fun getMetrics() = metricsStorage.getFontMetrics(isSmall)
  protected open fun getScaleFactor() = (getMetrics().fontHeight.toDouble() / icon.iconHeight)

  override val width: Int
    get() = (icon.iconWidth * getScaleFactor()).toInt()
  override val height: Int
    get() = getMetrics().fontHeight

  override fun paint(g: Graphics2D, attributes: TextAttributes) {
    val graphics = g.create() as Graphics2D
    graphics.composite = AlphaComposite.SrcAtop.derive(1.0f)
    val scaledIcon = IconUtil.scale(icon, component, (getScaleFactor()).toFloat())
    scaledIcon.paintIcon(component, graphics, 0, 0)
    graphics.dispose()
  }

  override fun toString(): String = "<image>"
}

@ApiStatus.Internal
class ScaledIconWithCustomFactorPresentation(
  metricsStorage: InlayTextMetricsStorage,
  isSmall: Boolean,
  icon: Icon,
  component: Component,
  private val iconScaleFactor: Float = 1f) : ScaledIconPresentation(metricsStorage, isSmall, icon, component) {

  override val height: Int
    get() = ceil(super.height * iconScaleFactor).toInt()

  override fun getScaleFactor(): Double = super.getScaleFactor() * iconScaleFactor
}