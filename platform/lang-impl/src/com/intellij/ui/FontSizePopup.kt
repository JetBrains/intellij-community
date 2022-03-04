// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("FontSizePopup")
@file:ApiStatus.Internal

package com.intellij.ui

import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBSlider
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.FlowLayout
import java.awt.MouseInfo
import java.awt.Point
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

fun <T> showFontSizePopup(
  parentComponent: Component,
  initialFont: T,
  fontRange: List<T>,
  onPopupClose: () -> Unit,
  changeCallback: (T) -> Unit
): FontSizePopupData {
  val slider = JBSlider(0, fontRange.size - 1).apply {
    orientation = SwingConstants.HORIZONTAL
    isOpaque = true
    minorTickSpacing = 1
    paintTicks = true
    paintTrack = true
    snapToTicks = true
  }
  UIUtil.setSliderIsFilled(slider, true)
  val initialIndex = fontRange.indexOf(initialFont)
  slider.setValueWithoutEvents(initialIndex)
  slider.addChangeListener {
    changeCallback(fontRange[slider.value])
  }

  val panel = JPanel(FlowLayout(FlowLayout.RIGHT, 3, 0))
  panel.isOpaque = true
  panel.add(JLabel(ApplicationBundle.message("label.font.size")))
  panel.add(slider)
  panel.border = BorderFactory.createLineBorder(JBColor.border(), 1)

  val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, slider).createPopup()
  popup.addListener(object : JBPopupListener {
    override fun onClosed(event: LightweightWindowEvent) {
      onPopupClose()
    }
  })

  val location = MouseInfo.getPointerInfo().location
  popup.show(
    RelativePoint(Point(
      location.x - panel.preferredSize.width / 2,
      location.y - panel.preferredSize.height / 2
    )).getPointOn(parentComponent)
  )

  return FontSizePopupData(slider)
}

data class FontSizePopupData(val slider: JBSlider)
